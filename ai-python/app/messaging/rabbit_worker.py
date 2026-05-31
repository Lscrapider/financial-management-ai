from concurrent.futures import Future, ThreadPoolExecutor
import json
import logging
from collections.abc import Callable
from typing import Any

import pika

from app.core.config import RabbitMqSettings
from app.messaging.errors import PermanentMessageError, RetryableMessageError
from app.messaging.models import HandlerResult, HandlerRoute, IncomingMessage
from app.messaging.registry import HandlerRegistry

logger = logging.getLogger(__name__)


class RabbitMqWorker:
    """通用 RabbitMQ worker。

    主线程只负责消费消息和提交任务，具体业务逻辑在线程池中的 handler 执行。
    handler 返回后续消息，由 worker 统一发布并在发布成功后 ack 当前消息。
    """

    def __init__(
        self,
        settings: RabbitMqSettings,
        routes: list[HandlerRoute],
        registry: HandlerRegistry,
        task_deleted_checker: Callable[[str], bool] | None = None,
    ) -> None:
        self._settings = settings
        self._routes = routes
        self._registry = registry
        self._task_deleted_checker = task_deleted_checker
        self._executor = ThreadPoolExecutor(max_workers=settings.handler_threads)
        self._connection: pika.BlockingConnection | None = None
        self._channel: pika.adapters.blocking_connection.BlockingChannel | None = None

    def run(self) -> None:
        credentials = pika.PlainCredentials(self._settings.username, self._settings.password)
        parameters = pika.ConnectionParameters(
            host=self._settings.host,
            port=self._settings.port,
            virtual_host=self._settings.virtual_host,
            credentials=credentials,
        )
        self._connection = pika.BlockingConnection(parameters)
        self._channel = self._connection.channel()
        self._channel.basic_qos(prefetch_count=self._settings.prefetch_count)
        for route in self._routes:
            self._channel.basic_consume(
                queue=route.queue,
                on_message_callback=lambda channel, method, properties, body, route=route: self._on_message(
                    route, method, body
                ),
                auto_ack=False,
            )
            logger.info("listening queue=%s routing_key=%s", route.queue, route.routing_key)
        self._channel.start_consuming()

    def _on_message(self, route: HandlerRoute, method: Any, body: bytes) -> None:
        delivery_tag = method.delivery_tag
        try:
            payload = json.loads(body.decode("utf-8"))
            if not isinstance(payload, dict):
                raise PermanentMessageError("message body must be a JSON object")
        except Exception as exc:
            logger.exception("invalid message body delivery_tag=%s", delivery_tag)
            self._reject(delivery_tag, requeue=False)
            return

        attempt = int(payload.get("attempt") or 1)
        message = IncomingMessage(
            body=payload,
            routing_key=method.routing_key,
            delivery_tag=delivery_tag,
            attempt=attempt,
        )
        # RabbitMQ channel 不是线程安全的，handler 在线程池中只执行业务，不直接 ack 或 publish。
        future = self._executor.submit(self._dispatch, route, message)
        future.add_done_callback(lambda completed: self._schedule_finalize(message, completed))

    def _dispatch(self, route: HandlerRoute, message: IncomingMessage) -> HandlerResult:
        if self._task_deleted_checker is not None and message.task_no:
            if self._task_deleted_checker(message.task_no):
                logger.info(
                    "skip soft deleted task message task_no=%s stage=%s routing_key=%s",
                    message.task_no,
                    message.stage,
                    message.routing_key,
                )
                return HandlerResult()
        handler = self._registry.get(route.handler_key)
        if handler is None:
            raise PermanentMessageError(f"no handler registered for {route.handler_key}")
        # 根据routing key dispatch到对应的handle
        return handler.handle(message)

    def _schedule_finalize(self, message: IncomingMessage, future: Future[HandlerResult]) -> None:
        if self._connection is None:
            return
        # 把 ack、reject、publish 切回消费连接线程执行，避免跨线程操作 channel。
        self._connection.add_callback_threadsafe(lambda: self._finalize(message, future))

    def _finalize(self, message: IncomingMessage, future: Future[HandlerResult]) -> None:
        try:
            result = future.result()
            # 后续消息出口：例如 document.normalize 成功后发布 ocr.recognize。
            self._publish_all(result)
            # 只有业务处理和后续消息发布都成功，才确认当前消息。
            self._ack(message.delivery_tag)
        except RetryableMessageError:
            logger.exception("retryable message failure task_no=%s stage=%s", message.task_no, message.stage)
            if message.attempt >= self._settings.max_attempts:
                self._reject_to_dead_letter(message)
                return
            self._publish_retry_and_ack(message)
        except Exception:
            logger.exception("permanent message failure task_no=%s stage=%s", message.task_no, message.stage)
            self._reject_to_dead_letter(message)

    def _publish_all(self, result: HandlerResult) -> None:
        if self._channel is None:
            raise RuntimeError("RabbitMQ channel is not ready")
        for outgoing in result.outgoing_messages:
            self._channel.basic_publish(
                exchange=outgoing.exchange,
                routing_key=outgoing.routing_key,
                body=json.dumps(outgoing.body, ensure_ascii=False).encode("utf-8"),
                properties=pika.BasicProperties(
                    content_type="application/json",
                    delivery_mode=pika.DeliveryMode.Persistent,
                ),
            )

    def _publish_retry_and_ack(self, message: IncomingMessage) -> None:
        if self._channel is None:
            return
        retry_body = dict(message.body)
        retry_body["attempt"] = message.attempt + 1
        # retry 队列依赖 TTL + DLX 回到原业务 exchange，原消息发布 retry 成功后 ack。
        self._channel.basic_publish(
            exchange=self._settings.retry_exchange,
            routing_key=f"{message.routing_key}.retry",
            body=json.dumps(retry_body, ensure_ascii=False).encode("utf-8"),
            properties=pika.BasicProperties(
                content_type="application/json",
                delivery_mode=pika.DeliveryMode.Persistent,
            ),
        )
        logger.info(
            "message published to retry task_no=%s stage=%s routing_key=%s next_attempt=%s",
            message.task_no,
            message.stage,
            f"{message.routing_key}.retry",
            retry_body["attempt"],
        )
        self._ack(message.delivery_tag)

    def _reject_to_dead_letter(self, message: IncomingMessage) -> None:
        # requeue=False 会触发当前队列的 x-dead-letter-exchange，最终进入 DLQ。
        logger.info(
            "message rejected to dead letter task_no=%s stage=%s routing_key=%s attempt=%s",
            message.task_no,
            message.stage,
            message.routing_key,
            message.attempt,
        )
        self._reject(message.delivery_tag, requeue=False)

    def _ack(self, delivery_tag: int) -> None:
        if self._channel is not None and self._channel.is_open:
            self._channel.basic_ack(delivery_tag=delivery_tag)

    def _reject(self, delivery_tag: int, requeue: bool) -> None:
        if self._channel is not None and self._channel.is_open:
            self._channel.basic_reject(delivery_tag=delivery_tag, requeue=requeue)
