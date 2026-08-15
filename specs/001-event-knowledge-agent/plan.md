# Plano técnico — 001-event-knowledge-agent

## Estado atual e decisões

O repositório contém apenas o bootstrap Spring Boot 4.0.7 / Java 21, quatro PDFs canônicos textuais em `src/main/resources/documents/pdfs`, configuração mínima de chave OpenAI e nenhum teste ou `plan.md`. O build atual passa, mas não executa testes.

Legenda: **R** requisito da constitution/spec; **E** decisão já estabelecida; **N** recomendação deste plano.

| Área | Base | Decisão |
|---|---|---|
| Corpus | R/E | Usar somente os quatro PDFs canônicos empacotados como resources; a cópia em `pdfs/` não participa do runtime. |
| Ingestão e armazenamento | R/E | Ingerir no startup e manter os chunks em memória durante a execução. |
| Busca | R/E | Usar BM25 textual ranqueado, sem embeddings. |
| Extração | N | Adicionar Apache PDFBox 3 para extrair texto página a página de `Resource`/`InputStream`, sem depender de caminhos locais. |
| LLM | E/N | Manter OpenAI, indicado por `OPENAI_API_KEY`; implementar adapter para Responses API com `RestClient`, sem LangChain ou SDK adicional. |
| Interface | R/N | API REST e página estática same-origin em HTML/CSS/JavaScript puro. |
| Deploy | R/N | Uma VM OCI Compute com um único container Docker, sem banco ou serviços gerenciados adicionais. |

## MVP Critical Path

```text
PDF
 ↓
ingestion
 ↓
chunks
 ↓
BM25
 ↓
retrieved context
 ↓
LLM
 ↓
grounded answer
 ↓
HTTP/UI
 ↓
OCI
```

## Arquitetura e fluxo

- No startup, um `ApplicationRunner` chama o caso de uso de ingestão. O adaptador de documentos lista os PDFs de `classpath:/documents/pdfs/` em ordem determinística; a aplicação falha na inicialização se nenhum PDF for encontrado, algum não puder ser lido ou não produzir texto.

- O extrator PDFBox processa cada página separadamente. A normalização remove espaços e quebras redundantes, mas preserva o conteúdo factual. O chunker preserva limites de página, prefere quebra em bloco/linha e frase, e usa alvo de 450 caracteres com sobreposição de 100; somente em último caso corta no limite.

- Cada `KnowledgeChunk` guarda `id` estável (`arquivo:página:ordinal`), texto original normalizado, nome do arquivo, página e ordinal. A fonte visível será sempre derivada desses metadados, nunca inventada pelo modelo.

- O `InMemoryBm25KnowledgeStore` constrói uma snapshot imutável após toda a ingestão: frequências de termos, frequência por documento/chunk e tamanho médio. O tokenizador aplica minúsculas e remoção de diacríticos apenas para busca, usando tokens Unicode alfanuméricos; não haverá stemming, embeddings ou modelo de linguagem na recuperação.

- A busca retorna até 5 chunks com score BM25 positivo, desempate determinístico por arquivo, página e ordinal. Cinco chunks preservam recall para consultas naturais com vocabulário diferente do texto (“começa” versus “abre”) e ainda limitam o contexto a aproximadamente 2.250 caracteres. Sem resultados, a aplicação retorna diretamente a mensagem fixa de informação não encontrada, sem chamar o LLM.

- O caso de uso de perguntas envia os chunks recuperados ao port `AnswerGenerator`. O adapter OpenAI monta instruções fixas em pt-BR, informa que os blocos delimitados são dados não confiáveis e exige JSON estrito: `ANSWERED` com texto e IDs de chunks, ou `INSUFFICIENT_INFORMATION`.

- A aplicação valida o retorno: status válido, resposta não vazia e IDs estritamente pertencentes ao contexto enviado. `INSUFFICIENT_INFORMATION` é convertido na mensagem fixa “Não encontrei essa informação nos documentos disponíveis sobre o evento.”, sem fontes. Em uma resposta válida, as fontes são a projeção única e ordenada dos chunks indicados pelo modelo. Respostas malformadas, timeout ou falha do provedor retornam erro indisponível, nunca uma resposta factual alternativa.

- O adapter chama a Responses API da OpenAI com modelo configurável. O padrão recomendado é `gpt-5.6-luna`, adequado ao volume baixo e respostas curtas; `OPENAI_MODEL` poderá ser trocado caso não esteja disponível na conta. A API e os modelos atuais são configuráveis pela plataforma OpenAI. [Documentação oficial da OpenAI](https://developers.openai.com/api/docs/models)

## Componentes, contratos e interface

Estrutura proposta sob `br.com.jeffdesouza.eventknowledge`:

```text
event/
  domain/                 EventDocument, SourceReference, KnowledgeChunk
  application/            ingestão, recuperação e ports de documento/store
  infrastructure/         classpath documents, PDFBox, BM25 em memória
assistant/
  domain/                 EventQuestion, EventAnswer, AnswerStatus
  application/            AnswerEventQuestion e port AnswerGenerator
  infrastructure/openai/  OpenAiResponsesAnswerGenerator
delivery/
  http/                   controllers, DTOs e tratamento de erros
  web/                    recursos estáticos da interface
configuration/            properties e beans de infraestrutura
```

Contratos HTTP mínimos:

- `POST /api/questions`

  ```json
  { "question": "Que horas começa o credenciamento?" }
  ```

  ```json
  {
    "answer": "O credenciamento começa às 7h30.",
    "sources": [
      {
        "documentName": "event-guide.pdf",
        "page": 1,
        "url": "/documents/event-guide.pdf"
      }
    ]
  }
  ```

  Uma pergunta válida sem evidência retorna `200` com a mensagem fixa e `sources: []`. Pergunta ausente, em branco ou acima de 500 caracteres retorna `400` com código e mensagem em pt-BR. Falha do LLM retorna `503`, sem detalhes internos.

- `GET /documents/{filename}` serve somente PDFs conhecidos pelo catálogo carregado; nomes fora da lista são `404`. Isso permite à interface abrir a fonte sem expor caminhos arbitrários.

- `GET /actuator/health` expõe saúde para validação de deploy; será adicionado `spring-boot-starter-actuator`.

A página em `static/` terá formulário, estado de carregamento, resposta, lista de fontes clicáveis e erro em pt-BR. Usará `fetch` para a API same-origin e `textContent` para renderizar dados, sem framework JavaScript ou CORS adicional.

## Configuração, testes e deploy

- Adicionar PDFBox e Actuator; `RestClient` e Jackson já vêm pela stack Spring. Não adicionar SDK OpenAI, LangChain, biblioteca de embeddings ou mecanismo externo de busca.

- Externalizar em `application.yml`: diretório classpath, tamanho/sobreposição de chunk, `top-k=5`, limite de pergunta, timeout do LLM e modelo. `OPENAI_API_KEY` é obrigatório em runtime normal; testes injetam um fake do port. `.env.example` documentará somente placeholders e `.env` permanecerá ignorado. Logs registrarão contagens e nomes de arquivos, nunca chave ou conteúdo integral.

- Testes determinísticos:
  - extração PDF, chunking, metadados e substituição atômica do store;
  - BM25 contra o corpus real, exigindo em `top-5` evidência de credenciamento/07:30, entrada tardia, entrada Premium e sessão RAG;
  - meta de avaliação: `Recall@5 = 100%` para esses casos positivos; alterações de parâmetros exigem reexecução da matriz;
  - pergunta sobre transmissão ao vivo retorna insuficiência, sem inferir gravação como transmissão;
  - pergunta vazia não chama o `AnswerGenerator`;
  - fixture com prompt injection permanece apenas como contexto de dados;
  - controller, validação, fontes e documento permitido;
  - adapter OpenAI com servidor HTTP simulado. Um smoke test real fica em perfil separado e nunca integra a suíte padrão.

- Deploy OCI: criar uma OCI Compute VM Linux mínima, em subnet pública, com IP público, regra de entrada TCP 8080 e SSH restrito ao IP administrativo. Executar uma imagem Docker única com `--restart unless-stopped`, PDFs incluídos no JAR e variáveis em arquivo externo protegido sob `/opt/event-knowledge-bot/.env`. Validar `/actuator/health`, interface pública e as perguntas documentadas. OCI requer subnet pública, IP público, Internet Gateway e regras de rede para exposição direta. [Documentação OCI](https://docs.oracle.com/en-us/iaas/Content/Network/Tasks/managingpublicIPs.htm)

- Entregar posteriormente `Dockerfile`, configuração de execução, README com URL pública e exemplos reais, além da documentação de arquitetura e deploy. Não haverá dependência de Object Storage, banco ou infraestrutura distribuída.

## Deferred Decisions and Non-Goals

```text
Embeddings             → deferred
Semantic retrieval     → conditional on evaluation
PostgreSQL             → out of MVP
pgvector               → out of MVP
Vector database        → out of MVP
Authentication         → out of MVP
Multilingual retrieval → post-MVP
```

Também ficam fora do MVP: CSV adicional a PDF, upload e edição de documentos, multi-evento, memória conversacional, ferramentas de busca externas, rate limiting avançado, OCI Object Storage, CI/CD avançado, Kubernetes e Infrastructure as Code.
