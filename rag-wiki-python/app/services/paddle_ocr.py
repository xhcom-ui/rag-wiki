"""
PaddleOCR增强服务

支持中文/仿宋/竖排等复杂场景的OCR识别，作为基础文档解析的增强模块。
可选安装：pip install paddleocr paddlepaddle
"""
import asyncio
import logging
import os
import tempfile
from typing import List, Optional, Dict, Any

logger = logging.getLogger(__name__)

# 尝试导入PaddleOCR
try:
    from paddleocr import PaddleOCR
    PADDLEOCR_AVAILABLE = True
except ImportError:
    PADDLEOCR_AVAILABLE = False
    logger.warning("PaddleOCR未安装，OCR增强功能不可用。安装: pip install paddleocr paddlepaddle")


class PaddleOCRService:
    """PaddleOCR增强服务"""

    def __init__(self):
        self._ocr_engine = None
        self._initialized = False

    def _ensure_init(self):
        """延迟初始化OCR引擎"""
        if self._initialized:
            return
        if not PADDLEOCR_AVAILABLE:
            logger.warning("PaddleOCR不可用，OCR增强功能将返回空结果")
            self._initialized = True
            return

        try:
            self._ocr_engine = PaddleOCR(
                use_angle_cls=True,     # 启用方向分类（支持竖排文字）
                lang='ch',              # 中文模型
                use_gpu=False,          # 默认CPU，有GPU可改True
                show_log=False,
                det_db_thresh=0.3,      # 检测阈值（较低以适应模糊文本）
                det_db_box_thresh=0.5,  # 文本框阈值
                rec_batch_num=6,        # 识别批处理大小
            )
            logger.info("PaddleOCR引擎初始化成功")
        except Exception as e:
            logger.error(f"PaddleOCR引擎初始化失败: {e}")
            self._ocr_engine = None
        self._initialized = True

    async def extract_text_from_image(
        self,
        image_path: str,
        enhance_mode: str = "default"
    ) -> Dict[str, Any]:
        """
        从图片中提取文字

        Args:
            image_path: 图片文件路径
            enhance_mode: 增强模式 - default/vertical(竖排)/document(文档)/handwriting(手写)

        Returns:
            OCR结果字典
        """
        self._ensure_init()

        if not self._ocr_engine:
            return {"text": "", "confidence": 0.0, "available": False}

        if not os.path.exists(image_path):
            logger.error(f"图片文件不存在: {image_path}")
            return {"text": "", "confidence": 0.0, "error": "文件不存在"}

        try:
            # 在线程池中执行同步OCR
            result = await asyncio.get_event_loop().run_in_executor(
                None, self._sync_ocr, image_path, enhance_mode
            )
            return result
        except Exception as e:
            logger.error(f"OCR处理失败: {e}")
            return {"text": "", "confidence": 0.0, "error": str(e)}

    def _sync_ocr(self, image_path: str, enhance_mode: str) -> Dict[str, Any]:
        """同步OCR处理"""
        try:
            # 根据增强模式调整参数
            ocr = self._ocr_engine
            if enhance_mode == "vertical":
                # 竖排文字：启用方向分类，调整检测参数
                ocr_result = ocr.ocr(image_path, cls=True)
            elif enhance_mode == "handwriting":
                # 手写体：降低检测阈值
                ocr_result = ocr.ocr(image_path, cls=True, det_db_thresh=0.2)
            else:
                # 默认/文档模式
                ocr_result = ocr.ocr(image_path, cls=True)

            if not ocr_result or not ocr_result[0]:
                return {"text": "", "confidence": 0.0, "blocks": []}

            # 解析结果
            texts = []
            blocks = []
            total_confidence = 0.0
            count = 0

            for line in ocr_result[0]:
                bbox = line[0]  # [[x1,y1],[x2,y2],[x3,y3],[x4,y4]]
                text = line[1][0]
                confidence = line[1][1]

                texts.append(text)
                total_confidence += confidence
                count += 1

                blocks.append({
                    "text": text,
                    "confidence": round(confidence, 4),
                    "bbox": bbox,
                    "direction": self._detect_direction(bbox),
                })

            avg_confidence = total_confidence / count if count > 0 else 0.0

            return {
                "text": "\n".join(texts),
                "confidence": round(avg_confidence, 4),
                "block_count": count,
                "blocks": blocks,
                "enhance_mode": enhance_mode,
                "available": True,
            }
        except Exception as e:
            logger.error(f"同步OCR处理失败: {e}")
            return {"text": "", "confidence": 0.0, "error": str(e)}

    async def batch_extract(
        self,
        image_paths: List[str],
        enhance_mode: str = "default"
    ) -> List[Dict[str, Any]]:
        """批量OCR处理"""
        tasks = [
            self.extract_text_from_image(path, enhance_mode)
            for path in image_paths
        ]
        return await asyncio.gather(*tasks)

    async def extract_from_pdf_page(
        self,
        pdf_path: str,
        page_num: int
    ) -> Dict[str, Any]:
        """从PDF指定页面提取文字（先转图片再OCR）"""
        try:
            # 尝试用PyMuPDF提取页面为图片
            import fitz  # PyMuPDF
            doc = fitz.open(pdf_path)
            if page_num < 0 or page_num >= len(doc):
                return {"text": "", "confidence": 0.0, "error": "页码超出范围"}

            page = doc[page_num]
            pix = page.get_pixmap(dpi=300)

            # 保存临时图片
            with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
                tmp_path = tmp.name
                pix.save(tmp_path)

            try:
                result = await self.extract_text_from_image(tmp_path, "document")
                result["page_num"] = page_num
                return result
            finally:
                os.unlink(tmp_path)
        except ImportError:
            logger.warning("PyMuPDF未安装，无法从PDF提取图片进行OCR")
            return {"text": "", "confidence": 0.0, "error": "PyMuPDF未安装"}
        except Exception as e:
            logger.error(f"PDF页面OCR失败: {e}")
            return {"text": "", "confidence": 0.0, "error": str(e)}

    def _detect_direction(self, bbox: List[List[float]]) -> str:
        """根据文本框判断文字方向"""
        if not bbox or len(bbox) < 4:
            return "horizontal"
        # 计算宽高比
        width = abs(bbox[1][0] - bbox[0][0])
        height = abs(bbox[3][1] - bbox[0][1])
        if height > width * 1.5:
            return "vertical"
        return "horizontal"

    @property
    def is_available(self) -> bool:
        """检查PaddleOCR是否可用"""
        return PADDLEOCR_AVAILABLE


# 全局实例
paddle_ocr_service = PaddleOCRService()
