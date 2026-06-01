from datetime import datetime
from pathlib import Path
import json
import re
import tempfile
from typing import Any


KEEP_TYPES = {
    "heading",
    "paragraph",
    "list item",
}

DROP_TYPES = {
    "header",
    "footer",
    "table",
    "image",
    "caption",
    "figure",
    "formula",
    "watermark",
    "page number",
}

REFERENCE_SECTION_PATTERNS = [
    r"^参考文献$",
    r"^参考资料$",
    r"^引用文献$",
    r"^references$",
    r"^bibliography$",
    r"^works cited$",
]

MAX_CHUNK_CHARS = 1200
CHUNK_OVERLAP_CHARS = 150


class OpenDataLoaderPdfRecognizer:
    def recognize(self, task_no: str, pdf_bytes: bytes, message: dict[str, Any]) -> dict[str, Any]:
        with tempfile.TemporaryDirectory(prefix="opendataloader-pdf-") as temp_dir:
            temp_path = Path(temp_dir)
            pdf_path = temp_path / "source.pdf"
            output_dir = temp_path / "output"
            output_dir.mkdir(parents=True, exist_ok=True)
            pdf_path.write_bytes(pdf_bytes)

            json_path = self._convert_pdf_to_json(pdf_path, output_dir)
            data = json.loads(json_path.read_text(encoding="utf-8"))

        chunks = self._build_chunks(data)
        if not chunks:
            raise ValueError("opendataloader_pdf produced no text chunks")

        return self._build_result(task_no, chunks, message)

    def _convert_pdf_to_json(self, pdf_path: Path, output_dir: Path) -> Path:
        import opendataloader_pdf

        opendataloader_pdf.convert(
            input_path=[str(pdf_path)],
            output_dir=str(output_dir),
            format="json",
            include_header_footer=False,
            image_output="off",
            reading_order="xycut"
        )

        json_files = list(output_dir.glob("*.json"))
        if not json_files:
            raise FileNotFoundError(f"No JSON file found in {output_dir}")

        same_name = output_dir / f"{pdf_path.stem}.json"
        if same_name.exists():
            return same_name
        return max(json_files, key=lambda item: item.stat().st_mtime)

    def _build_chunks(self, data: dict[str, Any]) -> list[dict[str, Any]]:
        file_name = data.get("file name") or "document.pdf"
        title = data.get("title") or Path(file_name).stem
        elements = self._extract_content_elements(data)
        sections = self._build_sections(elements, title)
        sections = self._remove_empty_sections(sections)

        chunks: list[dict[str, Any]] = []
        current = ""
        current_page: int | None = None

        def flush_current() -> None:
            nonlocal current, current_page
            if current and current_page is not None:
                chunks.append(
                    {
                        "content": current.strip(),
                        "page": current_page,
                    }
                )
            current = ""
            current_page = None

        for section in sections:
            for paragraph in section.get("paragraphs", []):
                content = self._normalize_text(paragraph.get("content", ""), keep_paragraph_breaks=True)
                if not content:
                    continue

                page_no = self._require_page_no(paragraph.get("page"))
                if current_page is not None and page_no != current_page:
                    flush_current()
                if current_page is None:
                    current_page = page_no

                for unit in self._text_units(content):
                    if unit == "\n\n":
                        if current and not current.endswith("\n\n"):
                            current += "\n\n"
                        continue

                    if len(unit) > MAX_CHUNK_CHARS:
                        flush_current()
                        chunks.extend(self._split_long_unit_to_chunks(unit, page_no))
                        continue

                    if len(current) + len(unit) > MAX_CHUNK_CHARS:
                        flush_current()
                        current_page = page_no

                    current += unit

                if current and not current.endswith("\n\n"):
                    current += "\n\n"

        flush_current()
        return chunks

    def _extract_content_elements(self, data: dict[str, Any]) -> list[dict[str, Any]]:
        raw_elements = self._walk_elements(data.get("kids", []))
        elements: list[dict[str, Any]] = []

        for item in raw_elements:
            item_type = str(item.get("type", "")).lower().strip()
            if item_type in DROP_TYPES or item_type not in KEEP_TYPES:
                continue

            content = self._normalize_text(item.get("content", ""))
            if self._is_noise_text(content):
                continue

            element = {
                "type": item_type,
                "content": content,
                "page": item.get("page number"),
            }
            if item_type == "heading":
                element["heading_level"] = item.get("heading level") or 1
            elements.append(element)

        return elements

    def _walk_elements(self, node: Any) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []

        if isinstance(node, list):
            for item in node:
                result.extend(self._walk_elements(item))
            return result

        if not isinstance(node, dict):
            return result

        node_type = str(node.get("type", "")).lower().strip()
        result.append(node)

        if isinstance(node.get("kids"), list):
            result.extend(self._walk_elements(node["kids"]))
        if isinstance(node.get("list items"), list):
            result.extend(self._walk_elements(node["list items"]))
        if node_type != "table":
            if isinstance(node.get("rows"), list):
                result.extend(self._walk_elements(node["rows"]))
            if isinstance(node.get("cells"), list):
                result.extend(self._walk_elements(node["cells"]))

        return result

    def _build_sections(self, elements: list[dict[str, Any]], default_title: str) -> list[dict[str, Any]]:
        sections: list[dict[str, Any]] = []
        current_section: dict[str, Any] | None = None
        section_index = 1
        paragraph_index = 1

        def create_section(title: str, level: int, page: int | None) -> dict[str, Any]:
            nonlocal section_index
            section = {
                "section_id": f"sec_{section_index:04d}",
                "title": title,
                "level": level,
                "start_page": page,
                "end_page": page,
                "paragraphs": [],
            }
            section_index += 1
            return section

        for element in elements:
            element_type = element["type"]
            content = element["content"]
            page = element.get("page")

            if element_type == "heading":
                if current_section is not None:
                    sections.append(current_section)
                current_section = create_section(content, int(element.get("heading_level") or 1), page)
                continue

            if current_section is None:
                current_section = create_section(default_title, 0, page)

            current_section["paragraphs"].append(
                {
                    "paragraph_id": f"p_{paragraph_index:05d}",
                    "type": element_type,
                    "page": page,
                    "content": content,
                }
            )
            paragraph_index += 1

            if page is not None:
                if current_section["start_page"] is None:
                    current_section["start_page"] = page
                current_section["end_page"] = page

        if current_section is not None:
            sections.append(current_section)

        return sections

    def _remove_empty_sections(self, sections: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        total_sections = len(sections)

        for index, section in enumerate(sections):
            appears_late_by_section = index >= max(1, int(total_sections * 0.55))
            if self._is_reference_section_title(section.get("title", "")) and appears_late_by_section:
                break
            if section.get("paragraphs"):
                result.append(section)

        return result

    def _text_units(self, text: str) -> list[str]:
        if not text:
            return []

        units: list[str] = []
        paragraphs = re.split(r"\n\s*\n+", text)
        for paragraph in paragraphs:
            paragraph = paragraph.strip()
            if not paragraph:
                continue
            parts = re.split(r"(?<=[。！？；.!?;])\s*", paragraph)
            for part in parts:
                part = part.strip()
                if part:
                    units.append(part)
            units.append("\n\n")

        if units and units[-1] == "\n\n":
            units.pop()
        return units

    def _split_long_unit_to_chunks(
        self,
        unit: str,
        page_no: int,
        max_chars: int = MAX_CHUNK_CHARS,
        overlap_chars: int = CHUNK_OVERLAP_CHARS,
    ) -> list[dict[str, Any]]:
        chunks: list[str] = []
        start = 0
        while start < len(unit):
            end = start + max_chars
            chunk = unit[start:end].strip()
            if chunk:
                chunks.append(chunk)
            if end >= len(unit):
                break
            start = max(end - overlap_chars, start + 1)

        return [
            {
                "content": chunk,
                "page": page_no,
            }
            for chunk in chunks
        ]

    def _build_result(self, task_no: str, chunks: list[dict[str, Any]], message: dict[str, Any]) -> dict[str, Any]:
        source_pages = message.get("pages") or []
        page_count = int(message.get("pageCount") or len(source_pages) or self._max_page(chunks))
        pages_by_no: dict[int, dict[str, Any]] = {}

        for index in range(1, page_count + 1):
            source_page = self._find_source_page(source_pages, index)
            pages_by_no[index] = {
                "pageNo": index,
                "imageRef": source_page.get("imageRef") if source_page else {},
                "width": source_page.get("width") if source_page else None,
                "height": source_page.get("height") if source_page else None,
                "enabled": False,
                "segments": [],
                "rawContent": "",
                "usage": {},
            }

        for segment_no, chunk in enumerate(chunks, start=1):
            page_no = self._require_page_no(chunk.get("page"))
            if page_count > 0 and page_no > page_count:
                raise ValueError(f"OpenDataLoader page number out of range: {page_no} > {page_count}")
            page = pages_by_no.setdefault(
                page_no,
                {
                    "pageNo": page_no,
                    "imageRef": {},
                    "width": None,
                    "height": None,
                    "enabled": False,
                    "segments": [],
                    "rawContent": "",
                    "usage": {},
                },
            )
            page["enabled"] = True
            page["segments"].append(
                {
                    "segmentNo": segment_no,
                    "content": chunk["content"],
                    "confidence": 1.0,
                }
            )

        pages = [pages_by_no[page_no] for page_no in sorted(pages_by_no)]
        segment_count = len(chunks)
        usage = {
            "promptTokens": 0,
            "completionTokens": 0,
            "totalTokens": 0,
        }
        return {
            "taskNo": task_no,
            "engine": "opendataloader_pdf",
            "pageCount": len(pages),
            "pages": pages,
            "metrics": {
                "enabledPageCount": sum(1 for page in pages if page["enabled"]),
                "emptyPageCount": sum(1 for page in pages if not page["enabled"]),
                "segmentCount": segment_count,
                "avgConfidence": 1.0,
                "usage": usage,
            },
            "createdAt": datetime.now().isoformat(timespec="seconds"),
        }

    @staticmethod
    def _find_source_page(source_pages: list[dict[str, Any]], page_no: int) -> dict[str, Any] | None:
        for page in source_pages:
            if int(page.get("pageNo") or 0) == page_no:
                return page
        return None

    @staticmethod
    def _max_page(chunks: list[dict[str, Any]]) -> int:
        pages = [OpenDataLoaderPdfRecognizer._require_page_no(chunk.get("page")) for chunk in chunks]
        return max(pages) if pages else 0

    @staticmethod
    def _require_page_no(value: Any) -> int:
        try:
            page_no = int(value)
        except (TypeError, ValueError):
            raise ValueError(f"OpenDataLoader content element missing valid page number: {value}") from None
        if page_no < 1:
            raise ValueError(f"OpenDataLoader content element has invalid page number: {page_no}")
        return page_no

    def _normalize_text(self, text: str, keep_paragraph_breaks: bool = False) -> str:
        if not text:
            return ""

        text = text.replace("\u00a0", " ")
        text = text.replace("\r\n", "\n").replace("\r", "\n")

        if keep_paragraph_breaks:
            paragraphs = re.split(r"\n\s*\n+", text)
            cleaned_paragraphs = []
            for paragraph in paragraphs:
                paragraph = re.sub(r"[ \t]+", " ", paragraph)
                paragraph = re.sub(r"\n+", " ", paragraph)
                paragraph = self._remove_spaces_between_chinese(paragraph.strip())
                if paragraph:
                    cleaned_paragraphs.append(paragraph)
            return "\n\n".join(cleaned_paragraphs).strip()

        text = re.sub(r"[ \t]+", " ", text)
        text = re.sub(r"\n+", " ", text)
        return self._remove_spaces_between_chinese(text.strip())

    @staticmethod
    def _remove_spaces_between_chinese(text: str) -> str:
        if not text:
            return ""
        text = re.sub(r"(?<=[\u4e00-\u9fff])\s+(?=[\u4e00-\u9fff])", "", text)
        text = re.sub(r"(?<=[\u4e00-\u9fff])\s+(?=[，。！？；：、）】》])", "", text)
        text = re.sub(r"(?<=[（【《])\s+(?=[\u4e00-\u9fff])", "", text)
        return text

    def _is_noise_text(self, text: str) -> bool:
        value = self._normalize_text(text)
        if not value:
            return True
        if re.fullmatch(r"\d{1,4}", value):
            return True
        if re.fullmatch(r"Page\s+\d+(\s+of\s+\d+)?", value, flags=re.I):
            return True
        return bool(re.fullmatch(r"第\s*\d+\s*页", value))

    def _is_reference_section_title(self, title: str) -> bool:
        value = self._normalize_text(title).strip().lower()
        if not value:
            return False

        value = re.sub(r"^(第?[一二三四五六七八九十百]+[章节、.]?\s*)", "", value)
        value = re.sub(r"^\d+(\.\d+)*[、.\s]+", "", value)
        value = value.strip()

        return any(re.fullmatch(pattern, value, flags=re.I) for pattern in REFERENCE_SECTION_PATTERNS)
