from abc import ABC, abstractmethod

from sentence_transformers import SentenceTransformer


class BaseEmbeddingEngine(ABC):
    @abstractmethod
    def embed(self, texts: list[str]) -> list[list[float]]:
        ...


class SentenceTransformersEngine(BaseEmbeddingEngine):
    def __init__(self, model_name: str, device: str, batch_size: int) -> None:
        self._model = SentenceTransformer(model_name, device=device)
        self._batch_size = batch_size

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        embeddings = self._model.encode(
            texts,
            batch_size=self._batch_size,
            show_progress_bar=False,
            normalize_embeddings=True,
        )
        return [emb.tolist() for emb in embeddings]
