# Plano técnico — 001-event-knowledge-agent

## Estado atual e decisões

O repositório contém a implementação do MVP em Spring Boot 4.0.7 / Java 21,
os quatro PDFs canônicos em `src/main/resources/documents/pdfs`, retrieval
BM25 em memória, integração com a OpenAI, interface web, container Docker e
deployment público no Railway.

Legenda: **R** requisito da constitution/spec; **E** decisão já estabelecida; **N** recomendação deste plano.

| Área | Base | Decisão |
|---|---|---|
| Corpus | R/E | Usar somente os quatro PDFs canônicos empacotados em src/main/resources/documents/pdfs como fontes de conhecimento do runtime. |
| Ingestão e armazenamento | R/E | Ingerir no startup e manter os chunks em memória durante a execução. |
| Busca | R/E | Usar BM25 textual ranqueado, sem embeddings. |
| Extração | N | Adicionar Apache PDFBox 3 para extrair texto página a página de `Resource`/`InputStream`, sem depender de caminhos locais. |
| LLM | E/N | Manter OpenAI, indicado por `OPENAI_API_KEY`; implementar adapter para Responses API com `RestClient`, sem LangChain ou SDK adicional. |
| Interface | R/N | API REST e página estática same-origin em HTML/CSS/JavaScript puro. |
| Deploy | R/E | Railway executando a imagem Docker única a partir do repositório GitHub, com configuração de runtime por variáveis de ambiente e domínio HTTPS público gerenciado pela plataforma. |

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
Railway
```

## Arquitetura e fluxo

- No startup, um `ApplicationRunner` chama o caso de uso de ingestão. O adaptador usa `ResourcePatternResolver`/`Resource` com o padrão `classpath*:/documents/pdfs/*.pdf`, ordena os resultados por nome e trabalha somente com `Resource`/`InputStream`. Isso funciona tanto no desenvolvimento quanto com os PDFs empacotados em JAR/container, sem depender de `File`, `Path` ou diretórios físicos. A aplicação falha na inicialização se nenhum PDF for encontrado, algum não puder ser lido ou não produzir texto.

- O extrator PDFBox processa cada página separadamente. A normalização remove espaços e quebras redundantes, mas preserva o conteúdo factual. O chunker preserva limites de página, prefere quebra em bloco/linha e frase, e usa alvo de 450 caracteres com sobreposição de 100; somente em último caso corta no limite.

- Cada `KnowledgeChunk` guarda `id` estável (`arquivo:página:ordinal`), texto original normalizado, nome do arquivo, página e ordinal. Ele é um conceito técnico da aplicação/retrieval, não uma entidade de domínio do evento. `EventDocument` e `SourceReference` permanecem como conceitos conceitualmente próprios do evento. A fonte visível será sempre derivada dos metadados da aplicação, nunca inventada pelo modelo.

- O `InMemoryBm25KnowledgeStore` constrói uma snapshot imutável após toda a ingestão: frequências de termos, frequência por documento/chunk e tamanho médio. O tokenizador aplica minúsculas e remoção de diacríticos apenas para busca, usando tokens Unicode alfanuméricos; não haverá stemming, embeddings ou modelo de linguagem na recuperação.

- A busca retorna até 5 chunks com score BM25 positivo, com desempate determinístico por arquivo, página e ordinal. `top-k=5` é o baseline inicial para um corpus pequeno: favorece recall e limita o contexto a aproximadamente 2.250 caracteres. O valor será validado pela matriz de avaliação; BM25 não será tratado como busca semântica e não se presumirá que ele resolva sinônimos. Sem resultados, a aplicação retorna diretamente a mensagem fixa de informação não encontrada, sem chamar o LLM.

- O caso de uso de perguntas envia os chunks recuperados ao port `AnswerGenerator`. O adapter OpenAI usa Structured Outputs/JSON Schema da Responses API quando suportado pelo modelo configurado. O schema exige `status` (`ANSWERED` ou `INSUFFICIENT_INFORMATION`), `answer` quando aplicável e `evidenceChunkIds`. O prompt continua responsável por grounding, idioma pt-BR, tratamento dos blocos recuperados como dados não confiáveis e comportamento quando não houver informação suficiente; a forma estrutural é garantida pelo contrato/schema da integração, não apenas por instrução textual. Se o modelo configurado não suportar Structured Outputs, não haverá fallback silencioso para JSON exigido somente por prompt: a configuração deve ser corrigida ou o startup deve falhar com erro claro.

  O contrato estruturado terá forma equivalente a:

  ```json
  {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "status": { "type": "string", "enum": ["ANSWERED", "INSUFFICIENT_INFORMATION"] },
      "answer": { "type": ["string", "null"] },
      "evidenceChunkIds": { "type": "array", "items": { "type": "string" }, "uniqueItems": true }
    },
    "required": ["status", "answer", "evidenceChunkIds"]
  }
  ```

- A aplicação valida o retorno: status válido, resposta não vazia e IDs estritamente pertencentes ao contexto enviado. `INSUFFICIENT_INFORMATION` é convertido na mensagem fixa “Não encontrei essa informação nos documentos disponíveis sobre o evento.”, sem fontes. Em uma resposta válida, as fontes são a projeção única e ordenada dos chunks indicados pelo modelo. Respostas malformadas, timeout ou falha do provedor retornam erro indisponível, nunca uma resposta factual alternativa.

- O adapter chama a Responses API da OpenAI com modelo configurável. O padrão recomendado é `gpt-5.6-luna`, por ser adequado a respostas curtas e tarefa delimitada, com custo compatível com o MVP; `OPENAI_MODEL` poderá ser trocado caso não esteja disponível na conta. A arquitetura não depende de um único modelo concreto. A API e os modelos atuais são configuráveis pela plataforma OpenAI. [Documentação oficial da OpenAI](https://developers.openai.com/api/docs/models)

## Componentes, contratos e interface

Estrutura proposta sob `br.com.jeffdesouza.eventknowledge`:

```text
event/
  domain/                 EventDocument, SourceReference
  application/            ingestão, recuperação, KnowledgeChunk e ports de documento/store
  infrastructure/         classpath documents, PDFBox, BM25 em memória
assistant/
  domain/                 EventQuestion, EventAnswer, AnswerStatus
  application/            AnswerEventQuestion e port AnswerGenerator
  infrastructure/openai/  OpenAiResponsesAnswerGenerator
delivery/
  http/                   controllers, DTOs e tratamento de erros
configuration/            properties e beans de infraestrutura
```

A interface web não será representada como pacote Java: HTML, CSS e JavaScript permanecerão em `src/main/resources/static/`. `delivery/http` continuará responsável pelos controllers e contratos HTTP.

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

- `GET /actuator/health` expõe saúde para validação de deploy; será adicionado `spring-boot-starter-actuator`. Somente os endpoints necessários, inicialmente `health`, serão expostos publicamente; não haverá exposição ampla da superfície administrativa do Actuator.

A página em `src/main/resources/static/` terá formulário, estado de carregamento, resposta, lista de fontes clicáveis e erro em pt-BR. Usará `fetch` para a API same-origin e `textContent` para renderizar dados, sem framework JavaScript ou CORS adicional.

## Configuração, testes e deploy

- O Maven Wrapper será o mecanismo padrão do projeto para comandos de build local e de build voltado ao deploy. Os comandos documentados no projeto devem preferir `./mvnw` em ambientes Unix-like e `mvnw.cmd` no Windows.

- Adicionar `spring-boot-starter-restclient`, PDFBox e Actuator. O uso de Spring `RestClient` será uma dependência explícita, sem depender de transitividade acidental. Não adicionar SDK específico da OpenAI, LangChain, biblioteca de embeddings ou mecanismo externo de busca.

- Externalizar em `application.yml`: diretório classpath, tamanho/sobreposição de chunk, `top-k=5`, limite de pergunta, timeout do LLM e modelo. `OPENAI_API_KEY` é obrigatório em runtime normal; testes injetam um fake do port. `.env.example` documentará somente placeholders e `.env` permanecerá ignorado. Logs registrarão contagens e nomes de arquivos, nunca chave ou conteúdo integral.

- Testes determinísticos:
  - extração PDF, chunking, metadados e substituição atômica do store;
  - BM25 contra uma matriz de 12 perguntas de referência baseada exclusivamente nos quatro PDFs canônicos:

    | # | Pergunta | Documento esperado | Evidência esperada | Respondível |
    |---|---|---|---|---|
    | 1 | Que horas começa o credenciamento? | `event-guide.pdf` ou `faq.pdf` | credenciamento às 07:30 nos dois dias | Sim |
    | 2 | Posso entrar em uma sessão depois que ela começou? | `faq.pdf` | entrada normalmente permitida se houver assentos e não houver interrupção | Sim |
    | 3 | Onde fica a entrada Premium? | `venue-info.pdf` | Portão B, lado leste do edifício | Sim |
    | 4 | Qual é o horário do evento na quinta-feira? | `event-guide.pdf` | 08:00–19:00 | Sim |
    | 5 | Quando acontece RAG na Prática? | `event-program.pdf` | 11:25–12:15, sala Aurora, em 27 de agosto | Sim |
    | 6 | Participantes Premium precisam de inscrição adicional nos workshops? | `faq.pdf` ou `event-guide.pdf` | acesso sem inscrição adicional, sujeito à capacidade | Sim |
    | 7 | Qual é o nome da rede Wi-Fi? | `faq.pdf` ou `venue-info.pdf` | `FutureTech-Guest` | Sim |
    | 8 | O estacionamento é gratuito? | `venue-info.pdf` | estacionamento pago, 420 vagas, sujeito à disponibilidade | Sim |
    | 9 | O que fazer se eu perder a credencial? | `faq.pdf` ou `event-guide.pdf` | procurar o Credenciamento com documento oficial; substituição possível | Sim |
    | 10 | Há guarda-volumes para malas grandes? | `faq.pdf` | malas grandes não são aceitas | Sim |
    | 11 | O local possui acesso para pessoas com mobilidade reduzida? | `venue-info.pdf` | acesso sem degraus pelos Portões A e B e elevadores | Sim |
    | 12 | O evento terá transmissão ao vivo? | `faq.pdf` | os documentos mencionam gravações, mas não transmissão ao vivo | Não |

    Para cada caso positivo obrigatório, a evidência esperada deve aparecer em algum dos cinco primeiros chunks. O critério é `HitRate@5 = 100%` nos casos positivos obrigatórios; o caso não respondível deve resultar em insuficiência, sem atribuir uma fonte factual inexistente. Não será introduzida avaliação sofisticada além dessa matriz e da inspeção do rank obtido.
  - pergunta sobre transmissão ao vivo retorna insuficiência, sem inferir gravação como transmissão;
  - pergunta vazia não chama o `AnswerGenerator`;
  - fixture com prompt injection permanece apenas como contexto de dados;
  - controller, validação, fontes e documento permitido;
  - adapter OpenAI com servidor HTTP simulado. Um smoke test real fica em perfil separado e nunca integra a suíte padrão.

- Deploy Railway: conectar o serviço ao repositório GitHub e utilizar o
  `Dockerfile` do projeto como artefato canônico de build e execução.
  A aplicação deve aceitar a porta fornecida pela plataforma através de
  `${PORT:8080}`, preservando 8080 como fallback local.

  Configurar `OPENAI_API_KEY` e `OPENAI_MODEL` como Service Variables na
  Railway, sem versionar segredos. `PORT` é fornecida automaticamente pela
  plataforma e não deve ser configurada manualmente.

  Expor o serviço por Public Networking com domínio HTTPS gerado pela Railway.
  Validar externamente `/actuator/health`, a interface web, uma pergunta
  respondível, a apresentação das fontes e a abertura do PDF referenciado.

  O deployment não requer VM administrada, SSH, configuração de firewall,
  proxy reverso, banco de dados ou serviços auxiliares.

- A entrega inclui `Dockerfile`, configuração de execução, README com URL
  pública e exemplos reais, além da documentação de arquitetura e deploy.
  Não há dependência de Object Storage, banco ou infraestrutura distribuída.

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

Também ficam fora do MVP: CSV adicional a PDF, upload e edição de documentos, multi-evento, memória conversacional, ferramentas de busca externas, rate limiting avançado, CI/CD avançado, Kubernetes e Infrastructure as Code.
