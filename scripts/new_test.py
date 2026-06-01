import json
import re
import tempfile
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Dict, List, Optional

import opendataloader_pdf


KEEP_TYPES = {
    "heading",
    "paragraph",
    "list",
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

REFERENCE_ITEM_PATTERNS = [
    r"^\[\d+\]\s*.+",
    r"^\d+[\.、]\s*.+",
]

MAX_CHUNK_CHARS = 1200
CHUNK_OVERLAP_CHARS = 150


# =========================
# Basic text cleaning
# =========================

def remove_spaces_between_chinese(text: str) -> str:
    """
    Remove accidental spaces between Chinese characters caused by PDF extraction.
    Example: "股 债 利 差" -> "股债利差".
    """
    if not text:
        return ""

    text = re.sub(r"(?<=[\u4e00-\u9fff])\s+(?=[\u4e00-\u9fff])", "", text)
    text = re.sub(r"(?<=[\u4e00-\u9fff])\s+(?=[，。！？；：、）】》])", "", text)
    text = re.sub(r"(?<=[（【《])\s+(?=[\u4e00-\u9fff])", "", text)
    return text


def normalize_text(text: str, keep_paragraph_breaks: bool = False) -> str:
    """
    keep_paragraph_breaks=False:
        Compress newlines into spaces. Used for headings and checks.

    keep_paragraph_breaks=True:
        Preserve paragraph boundaries as blank lines. Used for section/chunk content.
    """
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
            paragraph = paragraph.strip()
            paragraph = remove_spaces_between_chinese(paragraph)
            if paragraph:
                cleaned_paragraphs.append(paragraph)

        return "\n\n".join(cleaned_paragraphs).strip()

    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n+", " ", text)
    text = text.strip()
    text = remove_spaces_between_chinese(text)
    return text


def is_noise_text(text: str) -> bool:
    s = normalize_text(text)
    if not s:
        return True

    if re.fullmatch(r"\d{1,4}", s):
        return True

    if re.fullmatch(r"Page\s+\d+(\s+of\s+\d+)?", s, flags=re.I):
        return True

    if re.fullmatch(r"第\s*\d+\s*页", s):
        return True

    return False


def is_reference_section_title(title: str) -> bool:
    """
    Detect reference/bibliography section heading.
    Do not include 文献综述 here, because it is usually a body section.
    """
    s = normalize_text(title).strip().lower()
    if not s:
        return False

    s = re.sub(r"^(第?[一二三四五六七八九十百]+[章节、.]?\s*)", "", s)
    s = re.sub(r"^\d+(\.\d+)*[、.\s]+", "", s)
    s = s.strip()

    return any(re.fullmatch(pattern, s, flags=re.I) for pattern in REFERENCE_SECTION_PATTERNS)


def is_reference_item(text: str) -> bool:
    """
    Remove single bibliography items like:
    [1] xxx
    1. xxx
    1、xxx
    """
    s = normalize_text(text)
    if not s:
        return False

    return any(re.fullmatch(pattern, s, flags=re.I) for pattern in REFERENCE_ITEM_PATTERNS)


# =========================
# URL / local file handling
# =========================

def is_http_url(path: str) -> bool:
    return path.startswith("http://") or path.startswith("https://")


def guess_file_name_from_url(file_url: str, default_name: str = "document.pdf") -> str:
    parsed = urllib.parse.urlparse(file_url)
    name = Path(urllib.parse.unquote(parsed.path)).name
    return name or default_name


def download_file_to_path(file_url: str, target_path: Path) -> Path:
    target_path.parent.mkdir(parents=True, exist_ok=True)

    with urllib.request.urlopen(file_url) as response:
        with target_path.open("wb") as f:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                f.write(chunk)

    return target_path


# =========================
# OpenDataLoader conversion
# =========================

def convert_pdf_to_json(
    pdf_path: str,
    output_dir: str,
    reading_order: str = "xycut",
    use_struct_tree: bool = False,
) -> Path:
    """
    Convert local PDF to OpenDataLoader JSON.
    OpenDataLoader requires a local file path, so HTTP/MinIO URL must be downloaded first.
    """
    pdf_path_obj = Path(pdf_path).resolve()
    output_dir_obj = Path(output_dir).resolve()
    output_dir_obj.mkdir(parents=True, exist_ok=True)

    if not pdf_path_obj.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path_obj}")

    convert_kwargs = {
        "input_path": [str(pdf_path_obj)],
        "output_dir": str(output_dir_obj),
        "format": "json",
        "include_header_footer": False,
        "image_output": "off",
        "reading_order": reading_order,
        "use_struct_tree": use_struct_tree,
        "keep_line_breaks": False,
        "threads": "1",
    }

    opendataloader_pdf.convert(**convert_kwargs)

    same_name = output_dir_obj / f"{pdf_path_obj.stem}.json"
    if same_name.exists():
        return same_name

    json_files = list(output_dir_obj.glob("*.json"))
    if not json_files:
        raise FileNotFoundError(f"No JSON file found in {output_dir_obj}")

    return max(json_files, key=lambda p: p.stat().st_mtime)


# =========================
# OpenDataLoader JSON parsing
# =========================

def walk_elements(node: Any) -> List[Dict[str, Any]]:
    """
    Recursively flatten OpenDataLoader JSON elements.
    No custom bbox sorting is applied here. We keep OpenDataLoader's own reading order.
    """
    result = []

    if isinstance(node, list):
        for item in node:
            result.extend(walk_elements(item))
        return result

    if not isinstance(node, dict):
        return result

    result.append(node)

    if isinstance(node.get("kids"), list):
        result.extend(walk_elements(node["kids"]))

    if isinstance(node.get("list items"), list):
        result.extend(walk_elements(node["list items"]))

    return result


def extract_content_elements(data: Dict[str, Any]) -> List[Dict[str, Any]]:
    raw_elements = walk_elements(data.get("kids", []))
    elements = []

    for item in raw_elements:
        item_type = str(item.get("type", "")).lower().strip()

        if item_type in DROP_TYPES:
            continue

        if item_type not in KEEP_TYPES:
            continue

        content = normalize_text(item.get("content", ""))

        if is_noise_text(content):
            continue

        if is_reference_item(content):
            continue

        element = {
            "type": item_type,
            "content": content,
            "page": item.get("page number"),
            "bounding_box": item.get("bounding box"),
        }

        if item_type == "heading":
            element["heading_level"] = item.get("heading level") or 1

        elements.append(element)

    return elements


# =========================
# Section building
# =========================

def build_sections(elements: List[Dict[str, Any]], default_title: str) -> List[Dict[str, Any]]:
    sections = []
    current_section: Optional[Dict[str, Any]] = None
    paragraph_index = 1

    def new_section(section_id: int, title: str, level: int, page: Any) -> Dict[str, Any]:
        return {
            "section_id": section_id,
            "title": title,
            "level": level,
            "start_page": page,
            "end_page": page,
            "paragraphs": [],
        }

    for element in elements:
        element_type = element["type"]
        content = element["content"]
        page = element.get("page")

        if element_type == "heading":
            if current_section is not None:
                sections.append(current_section)

            current_section = new_section(
                section_id=len(sections) + 1,
                title=content,
                level=int(element.get("heading_level") or 1),
                page=page,
            )
            continue

        if current_section is None:
            current_section = new_section(
                section_id=1,
                title=default_title,
                level=1,
                page=page,
            )

        current_section["paragraphs"].append({
            "paragraph_id": paragraph_index,
            "type": element_type,
            "page": page,
            "content": content,
            "bounding_box": element.get("bounding_box"),
        })
        paragraph_index += 1

        if page is not None:
            if current_section["start_page"] is None:
                current_section["start_page"] = page
            current_section["end_page"] = page

    if current_section is not None:
        sections.append(current_section)

    return sections


def remove_empty_and_reference_sections(sections: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Remove empty sections.
    Drop references section only if it appears near the end, to avoid removing body sections by mistake.
    """
    result = []
    total_sections = len(sections)

    for index, section in enumerate(sections):
        title = section.get("title", "")
        appears_late = index >= max(1, int(total_sections * 0.55))

        if is_reference_section_title(title) and appears_late:
            break

        if section.get("paragraphs"):
            result.append(section)

    return result


def build_structured_document(json_path: str) -> Dict[str, Any]:
    json_path_obj = Path(json_path)
    data = json.loads(json_path_obj.read_text(encoding="utf-8"))

    file_name = data.get("file name") or json_path_obj.name
    title = data.get("title") or Path(file_name).stem

    elements = extract_content_elements(data)
    sections = build_sections(elements, default_title=title)
    sections = remove_empty_and_reference_sections(sections)

    return {
        "file_name": file_name,
        "title": title,
        "number_of_pages": data.get("number of pages"),
        "sections": sections,
    }


# =========================
# Chunking and final output
# =========================

def merge_paragraphs_to_content(section: Dict[str, Any]) -> str:
    contents = []

    for para in section.get("paragraphs", []):
        content = normalize_text(para.get("content", ""), keep_paragraph_breaks=True)
        if content:
            contents.append(content)

    return "\n\n".join(contents).strip()


def split_text_to_chunks(
    text: str,
    max_chars: int = MAX_CHUNK_CHARS,
    overlap_chars: int = CHUNK_OVERLAP_CHARS,
) -> List[str]:
    text = normalize_text(text, keep_paragraph_breaks=True)
    if not text:
        return []

    if len(text) <= max_chars:
        return [text]

    units: List[str] = []
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

    chunks = []
    current = ""

    for unit in units:
        if unit == "\n\n":
            if current and not current.endswith("\n\n"):
                current += "\n\n"
            continue

        if len(current) + len(unit) <= max_chars:
            current += unit
            continue

        if current:
            chunks.append(current.strip())

        if len(unit) > max_chars:
            start = 0
            while start < len(unit):
                end = start + max_chars
                chunk = unit[start:end].strip()
                if chunk:
                    chunks.append(chunk)
                if end >= len(unit):
                    break
                start = max(end - overlap_chars, start + 1)
            current = ""
        else:
            if chunks and overlap_chars > 0:
                current = chunks[-1][-overlap_chars:] + unit
            else:
                current = unit

    if current:
        chunks.append(current.strip())

    return chunks


def build_integrated_document(structured_doc: Dict[str, Any]) -> Dict[str, Any]:
    file_name = structured_doc.get("file_name") or "document.pdf"
    document_name = Path(file_name).stem
    title = structured_doc.get("title") or document_name

    integrated_sections = []
    all_chunks = []
    global_chunk_index = 1

    for sec_index, section in enumerate(structured_doc.get("sections", []), start=1):
        section_id = sec_index
        section_title = section.get("title") or f"Section {sec_index}"
        section_level = int(section.get("level") or 1)
        start_page = section.get("start_page")
        end_page = section.get("end_page")
        section_content = merge_paragraphs_to_content(section)

        if not section_content:
            continue

        chunk_texts = split_text_to_chunks(section_content)
        chunks = []

        for chunk_text in chunk_texts:
            chunk = {
                "chunk_id": global_chunk_index,
                "document_name": document_name,
                "section_id": section_id,
                "section_title": section_title,
                "section_level": section_level,
                "page_start": start_page,
                "page_end": end_page,
                "content": chunk_text,
                "metadata": {
                    "file_name": file_name,
                    "document_name": document_name,
                    "title": title,
                    "section_title": section_title,
                    "section_level": section_level,
                    "page_start": start_page,
                    "page_end": end_page,
                },
            }
            chunks.append(chunk)
            all_chunks.append(chunk)
            global_chunk_index += 1

        integrated_sections.append({
            "section_id": section_id,
            "title": section_title,
            "level": section_level,
            "start_page": start_page,
            "end_page": end_page,
            "content": section_content,
            "chunks": chunks,
        })

    return {
        "document_name": document_name,
        "file_name": file_name,
        "title": title,
        "number_of_pages": structured_doc.get("number_of_pages"),
        "section_count": len(integrated_sections),
        "chunk_count": len(all_chunks),
        "sections": integrated_sections,
        "chunks": all_chunks,
    }


def export_integrated_markdown(integrated_doc: Dict[str, Any]) -> str:
    lines = []

    title = normalize_text(integrated_doc.get("title", ""))
    if title:
        lines.append(f"# {title}")

    for section in integrated_doc.get("sections", []):
        section_title = normalize_text(section.get("title", ""))
        section_level = int(section.get("level") or 1)
        heading_level = min(max(section_level, 1), 6)
        heading_prefix = "#" * heading_level

        if section_title and section_title != title:
            lines.append("")
            lines.append(f"{heading_prefix} {section_title}")

        content = normalize_text(section.get("content", ""), keep_paragraph_breaks=True)
        if content:
            lines.append("")
            lines.append(content)

    return "\n".join(lines).strip() + "\n"


# =========================
# Public service functions
# =========================

def process_local_pdf_to_memory(
    pdf_path: str,
    reading_order: str = "xycut",
    use_struct_tree: bool = False,
) -> Dict[str, Any]:
    with tempfile.TemporaryDirectory(prefix="pdf_parse_") as temp_dir:
        output_dir = Path(temp_dir) / "output"
        json_path = convert_pdf_to_json(
            pdf_path=pdf_path,
            output_dir=str(output_dir),
            reading_order=reading_order,
            use_struct_tree=use_struct_tree,
        )
        structured_doc = build_structured_document(str(json_path))
        integrated_doc = build_integrated_document(structured_doc)
        return integrated_doc


def process_pdf_url_to_memory(
    file_url: str,
    reading_order: str = "xycut",
    use_struct_tree: bool = False,
) -> Dict[str, Any]:
    with tempfile.TemporaryDirectory(prefix="minio_pdf_") as temp_dir:
        temp_dir_path = Path(temp_dir)
        file_name = guess_file_name_from_url(file_url)
        local_pdf_path = temp_dir_path / file_name

        download_file_to_path(file_url, local_pdf_path)

        return process_local_pdf_to_memory(
            pdf_path=str(local_pdf_path),
            reading_order=reading_order,
            use_struct_tree=use_struct_tree,
        )


def process_pdf_to_memory(
    pdf_or_url: str,
    reading_order: str = "xycut",
    use_struct_tree: bool = False,
) -> Dict[str, Any]:
    if is_http_url(pdf_or_url):
        return process_pdf_url_to_memory(
            file_url=pdf_or_url,
            reading_order=reading_order,
            use_struct_tree=use_struct_tree,
        )

    return process_local_pdf_to_memory(
        pdf_path=pdf_or_url,
        reading_order=reading_order,
        use_struct_tree=use_struct_tree,
    )


# =========================
# Local debug helper
# =========================

def process_pdf_debug(
    pdf_or_url: str,
    output_dir: str = "output",
    reading_order: str = "xycut",
    use_struct_tree: bool = False,
):
    output_dir_obj = Path(output_dir).resolve()
    output_dir_obj.mkdir(parents=True, exist_ok=True)

    integrated_doc = process_pdf_to_memory(
        pdf_or_url=pdf_or_url,
        reading_order=reading_order,
        use_struct_tree=use_struct_tree,
    )

    json_output = output_dir_obj / "integrated_document.json"
    json_output.write_text(
        json.dumps(integrated_doc, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    md_output = output_dir_obj / "integrated_document.md"
    md_output.write_text(
        export_integrated_markdown(integrated_doc),
        encoding="utf-8",
    )

    print(f"Final integrated JSON saved to: {json_output}")
    print(f"Final integrated Markdown saved to: {md_output}")
    print(f"Total sections: {integrated_doc.get('section_count')}")
    print(f"Total chunks: {integrated_doc.get('chunk_count')}")


if __name__ == "__main__":
    process_pdf_debug(
        "我国科技金融市场发展现状与优化对策研究.pdf",
        output_dir="output",
        reading_order="xycut",
        use_struct_tree=False,
    )