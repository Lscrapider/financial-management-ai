class MessageHandlingError(Exception):
    pass


class RetryableMessageError(MessageHandlingError):
    pass


class PermanentMessageError(MessageHandlingError):
    pass
