---
applyTo: '**'
---
# Instrucciones para Agentes (Spring AI / Java)

Estas instrucciones definen **cómo construir aplicaciones de IA en este repositorio** (Spring Boot 4 + Spring AI 2.0.0-M1, Java 21). El objetivo es que cualquier agente implemente features de IA con **tipado fuerte, observabilidad, pruebas y orquestación robusta**.

## Stack y restricciones

- **Lenguaje/Runtime:** Java 21.
- **Framework:** Spring Boot 4.0.1.
- **IA:** Spring AI 2.0.0-M1 (BOM). No introducir SDKs “ad hoc” si Spring AI ya lo soporta.
- **Build:** Maven Wrapper (`./mvnw`).
- **Repo Milestones:** se usa `spring-milestones` por ser M1.

## Reglas de implementación

- Preferir **abstracciones de Spring AI** (p.ej. `ChatClient`, `VectorStore`, convertidores, memoria, herramientas).
- Mantener **interfaces claras** y dependencias mínimas.
- Usar **records/POJOs** tipados para entradas/salidas; evitar `Map<String,Object>` salvo capa de interoperabilidad.
- Externalizar prompts como **recursos versionados** (archivos en `src/main/resources/prompts/`), no strings gigantes embebidos.
- No hardcodear secretos (API keys). Usar variables de entorno y `application.properties`.

## Convenciones de paquetes

- Código de aplicación bajo `src/main/java/com/example/demo/`.
- Recomendado:
  - `.../ai/` (clientes, prompts, herramientas, orquestación)
  - `.../rag/` (ingesta, splitters, vector store, retrieval)
  - `.../mcp/` (servidores/clients MCP y adaptadores)
  - `.../web/` (controllers)
  - `.../eval/` (evaluación/quality gates)

## Gestión de Memoria y Contexto

- **Advisors vs Manual History:** En Spring AI 2.0.0-M1+, EVITAR la gestión manual de `List<Message>` para el historial de conversación.
- Usar **`MessageChatMemoryAdvisor`** inyectándolo en el `ChatClient`.
- Para sesiones efímeras (request-scoped agents), instanciar **`MessageWindowChatMemory`** (en reemplazo de `InMemoryChatMemory`) dentro del servicio o flujo.
- Para persistencia, configurar un bean de `ChatMemory` (Redis, JDBC, etc.) y usar `conversationId` en el Advisor.

## Migración y Breaking Changes (2.0.0-M1)

Al trabajar con Spring AI 2.0.0-M1, tener en cuenta los siguientes cambios críticos respecto a v1.x:

1. **Temperatura por defecto eliminada:** Los modelos ya no aplican temperatura por defecto. Se debe configurar explícitamente (`spring.ai.openai.chat.options.temperature=0.7`).
2. **Modelo por defecto:** OpenAI ahora usa variantes "mini" (ej. `gpt-4o-mini` o similar) como default.
3. **Clases Renombradas/Movidas:**
   - `InMemoryChatMemory` -> **`MessageWindowChatMemory`** (para implementaciones en memoria simples).
   - TTS: `OpenAiAudioSpeechModel` -> implementa `TextToSpeechModel`. Parámetro `speed` ahora es `Double`.
4. **Advisors Builders:** Usar Builders para Advisors (`QuestionAnswerAdvisor.builder(...)`) en lugar de constructores públicos deprecados.
5. **Salida Estructurada:** Preferir siempre `ChatClient.call().entity(Class<T>)` para mapeo automático de JSON a Records.

## Prompting y salidas estructuradas (tipado fuerte)

- Guardar prompts en `src/main/resources/prompts/*.st` o `*.txt`.
- Cuando se requiera estructura:
  - Definir un `record` (o POJO) como contrato de salida.
  - Convertir la respuesta del modelo a objeto tipado con `BeanOutputConverter` (o convertidor apropiado de Spring AI).
  - Validar campos obligatorios y manejar fallos de parseo con errores claros.

## RAG empresarial (patrón)

Cuando el feature requiera conocimiento externo:

1. **Ingesta/ETL**
   - Preferir lectores/transformers de Spring AI.
   - Para documentos complejos (PDF/Office) incorporar Apache Tika si es necesario.
2. **Chunking**
   - Usar splitters provistos por Spring AI (y ajustar tamaños por tokens).
3. **Vector store**
   - Usar la abstracción `VectorStore` y un backend real (PGVector/Neo4j/Redis/etc.).
4. **Recuperación**
   - Implementar retrieval básico primero; luego mejorar con:
     - query transformation
     - reranking
     - filtros/metadatos

## Herramientas (Function Calling)

- Implementar “tools” como servicios Spring con contratos claros.
- Las herramientas deben ser:
  - deterministas (cuando aplique)
  - idempotentes (cuando sea posible)
  - observables (logs/metrics/traces)
- Validar input del modelo (no confiar en argumentos generados).

## Orquestación agéntica (equivalencias tipo LangGraph)

El repositorio debe soportar patrones agénticos sin depender de puertos inmaduros.

### Equivalencias recomendadas

- **ReAct / bucles simples (LangGraph simple loop):**
  - Usar bucles controlados con Spring AI (p.ej. advisors/llamadas recursivas) con:
    - límite de iteraciones
    - condiciones de parada
    - registro de cada “step”

- **Grafos cíclicos complejos (LangGraph StateGraph):**
  - Preferir **Spring AI Alibaba Graph** cuando se requiera modelar nodos/aristas/estado explícito.
  - Mantener estado como objeto tipado (“state”) y separar:
    - nodos (acciones)
    - transiciones (routing)
    - persistencia (si aplica)

- **Orquestación de larga duración + resiliencia (checkpoints / human-in-the-loop):**
  - Preferir un motor de workflow:
    - **Temporal (Java SDK)** para procesos que sobreviven reinicios y esperas largas.
    - **Spring State Machine** para flujos finitos y deterministas.

### Patrones agénticos que deben poder implementarse

- **Chain workflow** (secuencial)
- **Parallelization** (con `CompletableFuture` cuando sea útil)
- **Routing** (clasificación de intención → seleccionar flujo/herramienta)
- **Orchestrator-Workers** (descomposición y delegación)
- **Evaluator-Optimizer** (feedback loop con evaluación automatizada)

## MCP (Model Context Protocol)

- Preferir MCP para exponer herramientas/datos como capacidades desacopladas.
- MCP Server (Spring Boot) debe:
  - definir contratos estables
  - tener auth cuando corresponda
  - exponer capacidades de forma clara
- MCP Client (agente) debe:
  - descubrir capacidades
  - no acoplarse a endpoints internos

## Observabilidad (mínimo)

- Registrar cada interacción IA con:
  - requestId/correlationId
  - modelo usado
  - latencia
  - resultado (ok/error)
- Evitar loguear secretos o PII.
- Si se añade Micrometer/Tracing, instrumentar:
  - llamadas al modelo
  - herramientas
  - retrieval

## Testing y calidad

- Agregar pruebas con JUnit 5.
- Para integración:
  - usar **Testcontainers** cuando haya dependencias reales (DB vectorial, etc.).
- Añadir tests “AI-aware” cuando el feature lo requiera:
  - evaluación de relevancia/correctitud con criterios explícitos (usar `RelevancyEvaluator` de Spring AI Evaluation).
  - tests parametrizados con fixtures
  - tolerancias (no tests frágiles por texto exacto)

## Seguridad y configuración

- Variables de entorno preferidas:
  - `SPRING_AI_OPENAI_API_KEY`
- No commitear `.env` ni claves.
- `application.properties` debe referenciar variables (placeholder) y tener valores por defecto seguros.

## Comandos estándar (copiar/pegar)

- Compilar:
  - `./mvnw -q -DskipTests package`
- Test:
  - `./mvnw -q test`
- Ejecutar:
  - `SPRING_AI_OPENAI_API_KEY=... ./mvnw -q spring-boot:run`

## Checklist antes de entregar

- El build pasa con `./mvnw test`.
- No se agregaron secretos al repo.
- Los flujos de IA tienen límites (iteraciones/timeouts) y manejo de errores.
- Las herramientas validan inputs del modelo.
- Si aplica RAG: existe ingesta/retrieval reproducible (y tests).
