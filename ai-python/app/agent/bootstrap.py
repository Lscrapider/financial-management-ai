from app.agent.constants import AGENT_RUN_START_QUEUE, ROUTING_KEY_AGENT_RUN_START
from app.agent.handlers.agent_run_handler import AgentRunHandler
from app.messaging.models import HandlerRoute
from app.messaging.registry import HandlerRegistry


def register_agent_handlers(registry: HandlerRegistry) -> list[HandlerRoute]:
    registry.register(ROUTING_KEY_AGENT_RUN_START, AgentRunHandler())
    return [
        HandlerRoute(
            queue=AGENT_RUN_START_QUEUE,
            routing_key=ROUTING_KEY_AGENT_RUN_START,
            handler_key=ROUTING_KEY_AGENT_RUN_START,
        )
    ]
