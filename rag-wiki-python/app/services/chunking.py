"""
文本分块策略 - 支持多种分块方式
1. 语义分块 (Semantic Chunking) - 按语义边界切分
2. 固定分块 (Fixed Chunking) - 按固定长度+重叠切分
3. 递归分块 (Recursive Chunking) - LangChain风格的递归字符切分
4. 结构分块 (Structure Chunking) - 按文档结构(标题/段落/表格)切分
"""
import logging
import re
from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional
from dataclasses import dataclass, field

from app.core.config import settings

logger = logging.getLogger(__name__)


@dataclass
class Chunk:
    """文本分块数据结构"""
    chunk_id: str
    content: str
    metadata: Dict[str, Any] = field(default_factory=dict)
    char_count: int = 0
    token_estimate: int = 0

    def __post_init__(self):
        self.char_count = len(self.content)
        # 粗略估算token数: 中文约1.5字符/token, 英文约4字符/token
        chinese_chars = len(re.findall(r'[\u4e00-\u9fff]', self.content))
        other_chars = self.char_count - chinese_chars
        self.token_estimate = int(chinese_chars / 1.5 + other_chars / 4)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "chunk_id": self.chunk_id,
            "content": self.content,
            "metadata": self.metadata,
            "char_count": self.char_count,
            "token_estimate": self.token_estimate,
        }


class BaseChunker(ABC):
    """分块策略抽象基类"""

    @abstractmethod
    def chunk(self, text: str, document_id: str, **kwargs) -> List[Chunk]:
        """执行分块"""
        pass

    @staticmethod
    def _make_chunk_id(document_id: str, index: int) -> str:
        return f"{document_id}_chunk_{index:04d}"


class FixedChunker(BaseChunker):
    """
    固定长度分块 - 按字符数切分，支持重叠
    适用于: 通用场景、没有明显结构的文本
    """

    def __init__(
        self,
        chunk_size: int = None,
        chunk_overlap: int = None,
        separator: str = "\n",
    ):
        self.chunk_size = chunk_size or settings.CHUNK_SIZE
        self.chunk_overlap = chunk_overlap or settings.CHUNK_OVERLAP
        self.separator = separator

    def chunk(self, text: str, document_id: str, **kwargs) -> List[Chunk]:
        chunk_size = kwargs.get("chunk_size", self.chunk_size)
        overlap = kwargs.get("chunk_overlap", self.chunk_overlap)

        # 先按分隔符切分
        segments = text.split(self.separator)
        segments = [s.strip() for s in segments if s.strip()]

        chunks = []
        current_text = ""
        chunk_index = 0

        for segment in segments:
            # 如果当前段落+新段落不超过大小限制，合并
            if len(current_text) + len(self.separator) + len(segment) <= chunk_size:
                current_text = f"{current_text}{self.separator}{segment}".strip()
            else:
                # 保存当前块
                if current_text:
                    chunks.append(Chunk(
                        chunk_id=self._make_chunk_id(document_id, chunk_index),
                        content=current_text,
                        metadata={"strategy": "fixed", "chunk_index": chunk_index},
                    ))
                    chunk_index += 1

                # 处理超长段落
                if len(segment) > chunk_size:
                    sub_chunks = self._split_long_segment(segment, chunk_size, overlap)
                    for sub in sub_chunks:
                        chunks.append(Chunk(
                            chunk_id=self._make_chunk_id(document_id, chunk_index),
                            content=sub,
                            metadata={"strategy": "fixed", "chunk_index": chunk_index},
                        ))
                        chunk_index += 1
                    current_text = sub_chunks[-1][-overlap:] if overlap > 0 else ""
                else:
                    # 重叠部分
                    if overlap > 0 and current_text:
                        current_text = current_text[-overlap:] + self.separator + segment
                    else:
                        current_text = segment

        # 最后一块
        if current_text.strip():
            chunks.append(Chunk(
                chunk_id=self._make_chunk_id(document_id, chunk_index),
                content=current_text.strip(),
                metadata={"strategy": "fixed", "chunk_index": chunk_index},
            ))

        logger.info(f"固定分块完成: document_id={document_id}, chunks={len(chunks)}")
        return chunks

    @staticmethod
    def _split_long_segment(text: str, chunk_size: int, overlap: int) -> List[str]:
        """将超长段落切分为多个子段"""
        result = []
        start = 0
        while start < len(text):
            end = start + chunk_size
            result.append(text[start:end])
            start = end - overlap if overlap > 0 else end
        return result


class RecursiveChunker(BaseChunker):
    """
    递归字符分块 - LangChain风格
    按优先级尝试不同分隔符: 段落 > 换行 > 句号 > 空格 > 字符
    """

    SEPARATORS = ["\n\n", "\n", "。", ".", "！", "!", "？", "?", "；", ";", " ", ""]

    def __init__(self, chunk_size: int = None, chunk_overlap: int = None):
        self.chunk_size = chunk_size or settings.CHUNK_SIZE
        self.chunk_overlap = chunk_overlap or settings.CHUNK_OVERLAP

    def chunk(self, text: str, document_id: str, **kwargs) -> List[Chunk]:
        chunk_size = kwargs.get("chunk_size", self.chunk_size)
        overlap = kwargs.get("chunk_overlap", self.chunk_overlap)

        raw_chunks = self._recursive_split(text, self.SEPARATORS, chunk_size)

        # 合并过短的块，并添加重叠
        merged = self._merge_chunks(raw_chunks, chunk_size, overlap)

        chunks = []
        for idx, content in enumerate(merged):
            if content.strip():
                chunks.append(Chunk(
                    chunk_id=self._make_chunk_id(document_id, idx),
                    content=content.strip(),
                    metadata={"strategy": "recursive", "chunk_index": idx},
                ))

        logger.info(f"递归分块完成: document_id={document_id}, chunks={len(chunks)}")
        return chunks

    def _recursive_split(self, text: str, separators: List[str], chunk_size: int) -> List[str]:
        """递归切分"""
        if not text:
            return []

        if len(text) <= chunk_size:
            return [text]

        # 尝试当前分隔符
        for i, sep in enumerate(separators):
            if sep == "":
                # 最后手段：按字符硬切
                return [text[j:j + chunk_size] for j in range(0, len(text), chunk_size)]

            splits = text.split(sep)
            if len(splits) == 1:
                continue

            # 合并过短的分割结果
            result = []
            current = ""
            for split in splits:
                candidate = f"{current}{sep}{split}" if current else split
                if len(candidate) <= chunk_size:
                    current = candidate
                else:
                    if current:
                        result.append(current)
                    # 对仍然过长的部分递归
                    if len(split) > chunk_size:
                        result.extend(self._recursive_split(split, separators[i + 1:], chunk_size))
                        current = ""
                    else:
                        current = split
            if current:
                result.append(current)
            return result

        return [text]

    @staticmethod
    def _merge_chunks(chunks: List[str], chunk_size: int, overlap: int) -> List[str]:
        """合并过短的块"""
        if not chunks:
            return []

        merged = []
        current = chunks[0]

        for chunk in chunks[1:]:
            candidate = f"{current}\n\n{chunk}"
            if len(candidate) <= chunk_size:
                current = candidate
            else:
                merged.append(current)
                # 添加重叠
                if overlap > 0:
                    current = current[-overlap:] + "\n\n" + chunk
                else:
                    current = chunk

        if current:
            merged.append(current)

        return merged


class SemanticChunker(BaseChunker):
    """
    语义分块 - 按语义相似度边界切分
    原理: 将文本先切为小段，计算相邻段的语义相似度，在相似度低的边界切分
    需要: Embedding模型
    """

    def __init__(self, chunk_size: int = None, similarity_threshold: float = 0.5):
        self.chunk_size = chunk_size or settings.CHUNK_SIZE
        self.similarity_threshold = similarity_threshold
        self._embedder = None

    def _get_embedder(self):
        """延迟加载Embedding模型"""
        if self._embedder is None:
            from app.services.embedding import embedding_service
            self._embedder = embedding_service
        return self._embedder

    async def achunk(self, text: str, document_id: str, **kwargs) -> List[Chunk]:
        """异步语义分块"""
        chunk_size = kwargs.get("chunk_size", self.chunk_size)

        # 第一步: 切成小段(句子级别)
        sentences = self._split_to_sentences(text)
        if not sentences:
            return []

        # 第二步: 计算相邻句子的语义相似度
        embedder = self._get_embedder()
        embeddings = await embedder.embed_texts([s["content"] for s in sentences])

        # 第三步: 在相似度低于阈值的边界切分
        breakpoints = []
        for i in range(len(embeddings) - 1):
            similarity = self._cosine_similarity(embeddings[i], embeddings[i + 1])
            if similarity < self.similarity_threshold:
                breakpoints.append(i + 1)

        # 第四步: 合并为最终分块
        chunks = []
        start = 0
        chunk_index = 0

        for bp in breakpoints + [len(sentences)]:
            group = sentences[start:bp]
            content = " ".join(s["content"] for s in group)

            # 如果超长，进一步切分
            if len(content) > chunk_size * 1.5:
                sub_chunker = RecursiveChunker(chunk_size=chunk_size)
                sub_chunks = sub_chunker.chunk(content, document_id)
                for sc in sub_chunks:
                    sc.chunk_id = self._make_chunk_id(document_id, chunk_index)
                    sc.metadata = {"strategy": "semantic", "chunk_index": chunk_index}
                    chunks.append(sc)
                    chunk_index += 1
            else:
                if content.strip():
                    chunks.append(Chunk(
                        chunk_id=self._make_chunk_id(document_id, chunk_index),
                        content=content.strip(),
                        metadata={
                            "strategy": "semantic",
                            "chunk_index": chunk_index,
                            "sentence_range": (start, bp),
                        },
                    ))
                    chunk_index += 1

            start = bp

        logger.info(f"语义分块完成: document_id={document_id}, chunks={len(chunks)}")
        return chunks

    def chunk(self, text: str, document_id: str, **kwargs) -> List[Chunk]:
        """同步分块 - 回退到递归分块"""
        logger.warning("语义分块建议使用异步方法 achunk()，同步调用回退到递归分块")
        return RecursiveChunker().chunk(text, document_id, **kwargs)

    @staticmethod
    def _split_to_sentences(text: str) -> List[Dict[str, Any]]:
        """将文本切分为句子"""
        # 中英文句子分割
        pattern = r'(?<=[。！？.!?；;])\s*'
        raw_sentences = re.split(pattern, text)
        sentences = []
        for idx, s in enumerate(raw_sentences):
            s = s.strip()
            if s:
                sentences.append({"index": idx, "content": s})
        return sentences

    @staticmethod
    def _cosine_similarity(vec1: List[float], vec2: List[float]) -> float:
        """计算余弦相似度"""
        import math
        dot = sum(a * b for a, b in zip(vec1, vec2))
        norm1 = math.sqrt(sum(a * a for a in vec1))
        norm2 = math.sqrt(sum(b * b for b in vec2))
        if norm1 == 0 or norm2 == 0:
            return 0.0
        return dot / (norm1 * norm2)


class StructureChunker(BaseChunker):
    """
    结构化分块 - 按文档结构(标题层级/段落/表格)切分
    适用于: Markdown、技术文档、有明确结构的文本
    """

    # 标题正则
    HEADING_PATTERN = re.compile(r'^(#{1,6})\s+(.+)$', re.MULTILINE)

    def chunk(self, text: str, document_id: str, **kwargs) -> List[Chunk]:
        # 检测是否有标题结构
        headings = list(self.HEADING_PATTERN.finditer(text))

        if not headings:
            # 没有标题结构，回退到递归分块
            return RecursiveChunker().chunk(text, document_id, **kwargs)

        chunks = []
        chunk_index = 0

        for i, match in enumerate(headings):
            start = match.start()
            end = headings[i + 1].start() if i + 1 < len(headings) else len(text)

            section_text = text[start:end].strip()
            level = len(match.group(1))
            title = match.group(2).strip()

            # 如果章节过长，进一步切分
            if len(section_text) > (settings.CHUNK_SIZE * 2):
                sub_chunker = RecursiveChunker()
                sub_chunks = sub_chunker.chunk(section_text, document_id)
                for sc in sub_chunks:
                    sc.chunk_id = self._make_chunk_id(document_id, chunk_index)
                    sc.metadata = {
                        "strategy": "structure",
                        "chunk_index": chunk_index,
                        "heading": title,
                        "heading_level": level,
                    }
                    chunks.append(sc)
                    chunk_index += 1
            else:
                if section_text:
                    chunks.append(Chunk(
                        chunk_id=self._make_chunk_id(document_id, chunk_index),
                        content=section_text,
                        metadata={
                            "strategy": "structure",
                            "chunk_index": chunk_index,
                            "heading": title,
                            "heading_level": level,
                        },
                    ))
                    chunk_index += 1

        logger.info(f"结构化分块完成: document_id={document_id}, chunks={len(chunks)}")
        return chunks


class ChunkingService:
    """分块服务 - 统一分块入口"""

    def __init__(self):
        self._chunkers = {
            "fixed": FixedChunker,
            "recursive": RecursiveChunker,
            "semantic": SemanticChunker,
            "structure": StructureChunker,
        }

    def chunk(
        self,
        text: str,
        document_id: str,
        strategy: str = "recursive",
        chunk_size: int = None,
        chunk_overlap: int = None,
        **kwargs,
    ) -> List[Chunk]:
        """
        同步分块

        Args:
            text: 待分块文本
            document_id: 文档ID
            strategy: 分块策略 (fixed/recursive/semantic/structure)
            chunk_size: 分块大小
            chunk_overlap: 重叠大小
        """
        chunker_class = self._chunkers.get(strategy, RecursiveChunker)
        chunker = chunker_class(
            chunk_size=chunk_size or settings.CHUNK_SIZE,
            chunk_overlap=chunk_overlap or settings.CHUNK_OVERLAP,
        )
        return chunker.chunk(text, document_id, **kwargs)

    async def achunk(
        self,
        text: str,
        document_id: str,
        strategy: str = "recursive",
        chunk_size: int = None,
        chunk_overlap: int = None,
        **kwargs,
    ) -> List[Chunk]:
        """异步分块（支持语义分块）"""
        if strategy == "semantic":
            chunker = SemanticChunker(
                chunk_size=chunk_size or settings.CHUNK_SIZE,
                similarity_threshold=kwargs.get("similarity_threshold", 0.5),
            )
            return await chunker.achunk(text, document_id, **kwargs)
        return self.chunk(text, document_id, strategy, chunk_size, chunk_overlap, **kwargs)


# 全局服务实例
chunking_service = ChunkingService()
