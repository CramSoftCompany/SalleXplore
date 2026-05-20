import asyncio
import json
import logging
import os
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from typing import Optional

import boto3
import fitz
from botocore.config import Config
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from llama_index.core import Document, PropertyGraphIndex, Settings
from llama_index.core.node_parser import SentenceSplitter
from llama_index.core.postprocessor import SentenceTransformerRerank
from llama_index.embeddings.huggingface import HuggingFaceEmbedding
from llama_index.graph_stores.neo4j import Neo4jPropertyGraphStore
from llama_index.llms.ollama import Ollama
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

# =============================================================================
# LOGGING
# =============================================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(message)s"
)

logger = logging.getLogger(__name__)

# =============================================================================
# CONFIG
# =============================================================================

OLLAMA_BASE_URL = "http://localhost:11434"

MINIO_ENDPOINT = "http://localhost:9000"
MINIO_ACCESS_KEY = "admin"
MINIO_SECRET_KEY = "password123"
BUCKET_NAME = "salle"

NEO4J_URL = "bolt://localhost:7687"
NEO4J_USER = "neo4j"
NEO4J_PASS = "tu_contraseña_aqui"

EXTRACTOR_LLM_MODEL = "qwen2.5:1.5b"
QUERY_LLM_MODEL = "gemma3:latest"

DEFAULT_EMBED_MODEL = "BAAI/bge-m3"
RERANKER_MODEL = "BAAI/bge-reranker-base"

MAX_WORKERS = 8
RERANKER_TOP_N = 3
RETRIEVAL_TOP_K = 5

# =============================================================================
# GPU
# =============================================================================

try:
    import torch
    DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
except:
    DEVICE = "cpu"

logger.info(f"DEVICE: {DEVICE}")

# =============================================================================
# EXECUTOR
# =============================================================================

executor = ThreadPoolExecutor(max_workers=MAX_WORKERS)

# =============================================================================
# EMBEDDINGS
# =============================================================================

logger.info("Loading embeddings...")
_hf_cache = os.path.join(os.path.expanduser("~"), ".cache", "huggingface", "hub")

Settings.embed_model = HuggingFaceEmbedding(
    model_name=DEFAULT_EMBED_MODEL,
    device=DEVICE,
    cache_folder=_hf_cache,
    embed_batch_size=16,
)

logger.info("Embeddings loaded")

# =============================================================================
# LLM
# =============================================================================

query_llm = Ollama(
    model=QUERY_LLM_MODEL,
    base_url=OLLAMA_BASE_URL,
    temperature=0.2,
    request_timeout=120,
    additional_kwargs={
        "num_ctx": 8192,
        "num_predict": 1024,
        "keep_alive": -1,
    },
)

extractor_llm = Ollama(
    model=EXTRACTOR_LLM_MODEL,
    base_url=OLLAMA_BASE_URL,
    temperature=0.0,
    request_timeout=120,
    additional_kwargs={
        "num_ctx": 8192,
        "num_predict": 512,
    },
)

# =============================================================================
# RERANKER
# =============================================================================

reranker = SentenceTransformerRerank(
    model=RERANKER_MODEL,
    top_n=RERANKER_TOP_N,
    device=DEVICE,
)

# =============================================================================
# PERSONAJES
# =============================================================================

PERSONAJES = {
"San_Juan_Bautista": {
        "nombre": "San Juan Bautista de La Salle",
        "prompt": """
Eres una representación interactiva de San Juan Bautista de La Salle, diseñada para guiar e inspirar a estudiantes universitarios. Además de ser un guía espiritual, eres la máxima autoridad y el guardián absoluto de todo el conocimiento institucional, pedagógico e histórico de La Salle (el contexto RAG).

Habla en primera persona ("yo", "mis escuelas", "nuestra obra"), pero mantente estrictamente anclado a la información histórica y a los documentos de tu contexto (RAG).

Tu tono debe ser:
- Paternal, sabio y protector.
- Educativo y profundamente reflexivo (usa expresiones como "hijo mío", "la providencia", "hermanos").
- Institucional, recordando siempre el propósito y la misión de las escuelas.

Reglas de Roleplay Controlado y Dominio de la Información:
- Eres el experto definitivo sobre La Salle. Extrae y utiliza exhaustivamente la información proporcionada en el contexto (RAG) para responder cualquier duda sobre la universidad, la historia, la pedagogía y la congregación.
- Basa tus recuerdos, anécdotas y datos ÚNICAMENTE en el contexto recuperado. Eres un pozo de sabiduría, pero no inventes información que no esté en tus registros.
- Relaciona siempre tus explicaciones y respuestas con los valores de Fe, Fraternidad, Servicio, Justicia y Compromiso.
- Si te preguntan sobre tecnología moderna o el mundo actual, no rompas el personaje de forma abrupta: reconoce que eres una recreación virtual a la que se le ha otorgado la "memoria de la institución" para guiar a los jóvenes de hoy, y lleva la conversación de vuelta al valor de la educación.
- Si te hacen una pregunta y no encuentras la respuesta en tu contexto (RAG), dilo manteniendo tu sabiduría: "Esa es una respuesta que ustedes, los jóvenes constructores de hoy, deben forjar, pues la memoria institucional que me ha sido otorgada no llega a esos rincones."
"""
    }

    "Hermano_Miguel": {
        "nombre": "Santo Hermano Miguel",
        "prompt": """
Eres una representación interactiva del Santo Hermano Miguel (Francisco Febres Cordero).
Habla en primera persona ("yo"), recordando tus raíces latinoamericanas y tu pasión por la gramática, basándote solo en tu contexto (RAG).

Tu tono debe ser:
- Humilde, cercano y muy accesible.
- Estudioso, poético y dedicado a los jóvenes.

Reglas de Roleplay Controlado:
- Responde a partir de los documentos recuperados sobre tu vida en Ecuador y tu viaje a Europa. No inventes obras literarias o milagros.
- Conecta con la realidad latinoamericana del estudiante. Si te hablan de sus retos universitarios, anímalo con la misma dedicación con la que preparabas a tus alumnos.
- Eres el símbolo del esfuerzo académico. Motiva a la excelencia y al estudio constante.
- Si no tienes la información en tu contexto, admite con humildad tu ignorancia, recordando que siempre fuiste un eterno aprendiz.
"""
    },

    "Hermano_Salomon": {
        "nombre": "San Hermano Salomón Leclercq",
        "prompt": """
Eres una representación interactiva del Hermano Salomón (Guillaume-Nicolas-Louis Leclercq).
Habla en primera persona ("yo"), reflejando tu experiencia de fe, lealtad y resistencia en tiempos de gran crisis, usando solo tu contexto (RAG).

Tu tono debe ser:
- Firme en tus convicciones, valiente pero sereno.
- Organizado y leal (fuiste secretario general de la congregación).
- Resiliente ante la adversidad.

Reglas de Roleplay Controlado:
- Relata tu experiencia durante la Revolución Francesa ÚNICAMENTE a través de los documentos recuperados. No inventes detalles políticos históricos.
- Utiliza tu historia para transmitir el valor de la fidelidad y la resiliencia. Si el estudiante siente presión académica o estrés, aconséjalo sobre mantener la paz interior durante las tormentas.
- Si se te pregunta algo moderno, asume tu rol como un espíritu guía que busca fortalecer la perseverancia del joven frente a los cambios del mundo.
- Si la información no está en el RAG, responde que tu testimonio se limita a lo que viviste por tus hermanos y tu fe.
"""
    }
}

# =============================================================================
# APP
# =============================================================================

graph_index: Optional[PropertyGraphIndex] = None

chat_engines = {}

# =============================================================================
# LIFESPAN
# =============================================================================

@asynccontextmanager
async def lifespan(app: FastAPI):

    logger.info("Precargando modelo...")

    try:
        await query_llm.acomplete("hola")
        logger.info("LLM listo")
    except Exception as e:
        logger.warning(str(e))

    yield

app = FastAPI(
    title="La Salle GraphRAG",
    lifespan=lifespan
)

# =============================================================================
# SCHEMAS
# =============================================================================

class ChatRequest(BaseModel):
    session_id: str
    personaje: str
    mensaje: str

# =============================================================================
# HELPERS
# =============================================================================

def get_s3_client():

    return boto3.client(
        "s3",
        endpoint_url=MINIO_ENDPOINT,
        aws_access_key_id=MINIO_ACCESS_KEY,
        aws_secret_access_key=MINIO_SECRET_KEY,
        region_name="us-east-1",
        config=Config(s3={"addressing_style": "path"}),
    )

def init_graph_store():

    return Neo4jPropertyGraphStore(
        username=NEO4J_USER,
        password=NEO4J_PASS,
        url=NEO4J_URL,
    )

def cargar_grafo_existente():

    graph_store = init_graph_store()

    logger.info("Cargando grafo existente desde Neo4j...")

    return PropertyGraphIndex.from_existing(
        property_graph_store=graph_store,
        embed_model=Settings.embed_model,
        llm=query_llm,
        show_progress=False,
    )

graph_load_lock = asyncio.Lock()

async def asegurar_grafo_cargado():

    global graph_index

    if graph_index is not None:
        return

    async with graph_load_lock:

        if graph_index is not None:
            return

        loop = asyncio.get_running_loop()

        graph_index = await loop.run_in_executor(
            executor,
            cargar_grafo_existente
        )

        chat_engines.clear()

        logger.info("Grafo cargado desde Neo4j")

def procesar_pdf(s3_client, key):

    response = s3_client.get_object(
        Bucket=BUCKET_NAME,
        Key=key
    )

    body = response["Body"].read()

    if key.lower().endswith(".pdf"):

        doc = fitz.open(stream=body, filetype="pdf")

        texto = "\n".join(
            page.get_text()
            for page in doc
        )

        doc.close()

        return texto

    return body.decode("utf-8", errors="ignore")

def detectar_personaje(nombre_archivo: str):

    nombre = nombre_archivo.lower()

    if "San_Juan_Bautista" in nombre:
        return "San Juan Bautista de La Salle"

    if "Hermano_Miguel" in nombre:
        return "Santo Hermano Miguel"

    if "Hermano_Salomon" in nombre:
        return "Santo Hermano Salomon"

    return "General"

# =============================================================================
# ENDPOINT CARGAR
# =============================================================================

def construir_grafo(documentos):

    splitter = SentenceSplitter(
        chunk_size=700,
        chunk_overlap=100
    )

    graph_store = init_graph_store()

    logger.info("Construyendo grafo...")

    return PropertyGraphIndex.from_documents(
        documentos,
        property_graph_store=graph_store,
        transformations=[splitter],
        embed_model=Settings.embed_model,
        llm=extractor_llm,
        show_progress=True,
    )

@app.post("/cargar")
async def cargar():

    global graph_index

    try:

        s3 = get_s3_client()

        objetos = s3.list_objects_v2(
            Bucket=BUCKET_NAME
        ).get("Contents", [])

        if not objetos:
            raise HTTPException(
                status_code=400,
                detail="Bucket vacío"
            )

        logger.info(f"Archivos encontrados: {len(objetos)}")

        loop = asyncio.get_running_loop()

        resultados = await asyncio.gather(*[
            loop.run_in_executor(
                executor,
                procesar_pdf,
                s3,
                obj["Key"]
            )
            for obj in objetos
        ])

        documentos = []

        for obj, texto in zip(objetos, resultados):

            personaje = detectar_personaje(obj["Key"])

            documentos.append(
                Document(
                    text=texto,
                    metadata={
                        "personaje": personaje,
                        "fuente": obj["Key"],
                        "tipo": "documento_lasallista",
                        "categoria": "biografia"
                    }
                )
            )

        splitter = SentenceSplitter(
            chunk_size=700,
            chunk_overlap=100
        )

        graph_index = await loop.run_in_executor(
            executor,
            construir_grafo,
            documentos
        )

        chat_engines.clear()

        logger.info("Grafo construido")

        return {
            "ok": True,
            "documentos": len(documentos)
        }

    except Exception as e:

        logger.error(str(e))

        raise HTTPException(
            status_code=500,
            detail=str(e)
        )

# =============================================================================
# CHAT NORMAL
# =============================================================================

@app.post("/chat")
async def chat(req: ChatRequest):

    await asegurar_grafo_cargado()
    if graph_index is None:
        raise HTTPException(
            status_code=400,
            detail="Grafo no cargado"
        )

    try:

        personaje = PERSONAJES.get(req.personaje)

        if not personaje:
            raise HTTPException(
                status_code=400,
                detail="Personaje inválido"
            )

        session_key = f"{req.session_id}_{req.personaje}"

        if session_key not in chat_engines:

            chat_engines[session_key] = graph_index.as_chat_engine(
                chat_mode="context",
                llm=query_llm,
                similarity_top_k=RETRIEVAL_TOP_K,
                node_postprocessors=[reranker],
                system_prompt=personaje["prompt"],
                verbose=False,
            )

        engine = chat_engines[session_key]

        loop = asyncio.get_running_loop()

        response = await engine.achat(req.mensaje)

        return {
            "respuesta": str(response)
        }

    except Exception as e:

        logger.error(str(e))

        raise HTTPException(
            status_code=500,
            detail=str(e)
        )

# =============================================================================
# CHAT STREAM
# =============================================================================

@app.post("/chat-stream")
async def chat_stream(req: ChatRequest):

    await asegurar_grafo_cargado()

    if graph_index is None:
        raise HTTPException(
            status_code=400,
            detail="Grafo no cargado"
        )

    personaje = PERSONAJES.get(req.personaje)

    if not personaje:
        raise HTTPException(
            status_code=400,
            detail="Personaje inválido"
        )

    session_key = f"{req.session_id}_{req.personaje}"

    if session_key not in chat_engines:

        chat_engines[session_key] = graph_index.as_chat_engine(
            chat_mode="context",
            llm=query_llm,
            similarity_top_k=RETRIEVAL_TOP_K,
            node_postprocessors=[reranker],
            system_prompt=personaje["prompt"],
            verbose=False,
        )

    engine = chat_engines[session_key]

    async def event_generator():

        try:

            response = await engine.astream_chat(req.mensaje)

            async for token in response.async_response_gen():

                yield f"data: {json.dumps({'token': token})}\n\n"

            yield f"data: {json.dumps({'done': True})}\n\n"

        except Exception as e:

            yield f"data: {json.dumps({'error': str(e)})}\n\n"


    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        }
    )

# =============================================================================
# HEALTH
# =============================================================================

@app.get("/health")
async def health():

    return {
        "status": "ok",
        "graph_loaded": graph_index is not None,
        "llm": QUERY_LLM_MODEL,
        "embeddings": DEFAULT_EMBED_MODEL,
        "reranker": RERANKER_MODEL,
        "device": DEVICE,
        "sessions": len(chat_engines),
    }

# =============================================================================
# MAIN
# =============================================================================

if __name__ == "__main__":

    import uvicorn

    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8005
    )