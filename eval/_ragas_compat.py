"""ragas 0.4.x 在 langchain-community 0.4.x 下的兼容补丁。

ragas.llms.base 会尝试 from langchain_community.chat_models.vertexai import ChatVertexAI，
而新版 langchain-community 移除了该模块。本项目评测只用 ChatOpenAI（DashScope），
因此注册一个空桩模块即可让 ragas 正常导入。
"""
import sys
import types

_PATCHED = "_fitplan_ragas_vertexai_patched"


def apply():
    if _PATCHED in sys.modules:
        return
    try:
        from langchain_community.chat_models import vertexai  # noqa: F401
        sys.modules[_PATCHED] = True
        return
    except ImportError:
        pass

    stub = types.ModuleType("langchain_community.chat_models.vertexai")
    stub.ChatVertexAI = None
    sys.modules["langchain_community.chat_models.vertexai"] = stub

    chat_models = sys.modules.get("langchain_community.chat_models")
    if chat_models is not None:
        chat_models.vertexai = stub
    sys.modules[_PATCHED] = True


apply()
