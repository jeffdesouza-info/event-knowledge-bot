# Tasks — 001-event-knowledge-agent

## Objetivo e ordenação

Este backlog deriva de `spec.md` e do plano técnico aprovado. As tarefas estão
ordenadas pelo **MVP Critical Path** definido no plano:

```text
PDF → ingestão → chunks → BM25 → contexto recuperado → LLM
    → resposta fundamentada → HTTP/UI → OCI
```

Todas as tarefas abaixo são **P0**, pois compõem o caminho necessário para a
entrega demonstrável até **19/08/2026**. Não há tarefas P1 ou P2 neste arquivo:
as evoluções fora do MVP já estão explicitamente adiadas pelo plano e pela
especificação e não devem competir com a entrega.

## Decisões do plano a preservar durante a execução

- O corpus de runtime é formado exclusivamente pelos quatro PDFs canônicos
  empacotados em `src/main/resources/documents/pdfs`.
- Os documentos são carregados no startup por `Resource`/`InputStream`, sem
  caminhos locais, e os chunks ficam em memória durante a execução.
- A recuperação é BM25 textual em memória, com `top-k=5`, usando uma estratégia
  compartilhada de tokenização, remoção de stopwords comuns do português e
  normalização morfológica leve; continua sem embeddings, banco, vetores ou
  busca semântica.
- A extração usa PDFBox 3; o provedor é OpenAI via Responses API e `RestClient`,
  atrás do port `AnswerGenerator`, sem SDK OpenAI ou LangChain.
- O retorno do modelo usa Structured Outputs/JSON Schema e é validado antes de
  chegar ao usuário; não há fallback silencioso baseado apenas em prompt.
- A entrega é uma única aplicação Spring Boot, com REST, página estática
  same-origin e um container Docker em uma VM OCI Compute, sem serviços
  gerenciados adicionais.

## Convenções de execução

- **Dependências** indicam as tarefas que precisam estar concluídas antes do
  início da tarefa.
- **Validação** descreve evidência executável ou inspecionável que deve ser
  obtida na própria tarefa; não substitui a validação integrada do Delivery
  Gate.
- **Ponto natural de commit** é apenas uma sugestão de incremento coeso; não
  exige um commit por tarefa.

---

## 1. Fundação mínima e contratos do fluxo

### [ ] T001 — Preparar dependências e configuração externa do MVP

- **Prioridade:** P0
- **Requisitos:** FR-002, FR-003, FR-006, FR-011, VAL-002, SEC-001 a SEC-004, DEP-001, NFR-004, NFR-005
- **Dependências:** nenhuma
- **Trabalho esperado:** adicionar as dependências explícitas de PDFBox 3,
  `spring-boot-starter-restclient` e Actuator; criar `application.yml` com
  propriedades para padrão classpath dos documentos, tamanho/sobreposição de
  chunk, `top-k=5`, limite de pergunta, timeout e modelo OpenAI; vincular
  `OPENAI_API_KEY` somente à configuração externa de runtime e documentar
  placeholders em `.env.example`. Configurar exposição pública somente de
  `health` no Actuator e logs sem chave ou conteúdo integral dos documentos.
- **Validação:** `mvnw.cmd test` conclui com sucesso; a configuração não contém
  segredo real; a configuração efetiva deixa explícitos os valores definidos no
  plano.
- **Critério de conclusão:** o projeto compila com as dependências aprovadas e
  pode receber configuração de modelo/chave sem valores sensíveis versionados.

### [ ] T002 — Definir os conceitos e ports do núcleo da aplicação

- **Prioridade:** P0
- **Requisitos:** FR-005, FR-006, FR-007, FR-008, FR-010, AI-001, AI-005, NFR-001 a NFR-003
- **Dependências:** T001
- **Trabalho esperado:** criar a estrutura acordada em `event`, `assistant`,
  `delivery` e `configuration`; definir `EventDocument` e `SourceReference` no
  domínio do evento, `EventQuestion`, `EventAnswer` e `AnswerStatus` no domínio
  do assistente, e `KnowledgeChunk` como conceito técnico da aplicação de
  recuperação. Definir somente os ports necessários para catálogo/carregamento
  de documentos, extração, armazenamento/recuperação e `AnswerGenerator`, sem
  dependências de Spring, PDFBox ou OpenAI no domínio/aplicação.
- **Validação:** testes de unidade constroem os objetos e contratos; inspeção de
  imports confirma que domínio e aplicação não dependem de infraestrutura.
- **Critério de conclusão:** os contratos permitem implementar ingestão,
  busca e geração de respostas sem acoplamento a tecnologia externa.

**Ponto natural de commit (opcional):** `chore: prepare application configuration and contracts` após T001–T002.

---

## 2. PDF → ingestão → chunks

### [ ] T003 — Catalogar os PDFs canônicos como recursos de classpath

- **Prioridade:** P0
- **Requisitos:** FR-002, FR-005, FR-006, SEC-005, NFR-004
- **Dependências:** T001, T002
- **Trabalho esperado:** implementar o adapter que localiza exclusivamente
  `classpath*:/documents/pdfs/*.pdf` por `ResourcePatternResolver`, ordena o
  catálogo por nome e expõe recursos por `Resource`/`InputStream`. Registrar
  somente os quatro documentos canônicos carregados e disponibilizar a lista
  para validação posterior de fontes e download permitido.
- **Validação:** teste com recursos de classpath confirma ordenação
  determinística, os quatro nomes esperados e ausência de conversão para
  `File`/`Path`.
- **Critério de conclusão:** o catálogo é independente do sistema de arquivos
  local e contém somente documentos que poderão compor a base de conhecimento.

### [ ] T004 — Extrair texto PDF página a página com metadados de origem

- **Prioridade:** P0
- **Requisitos:** FR-003, FR-005, FR-010, AI-005, NFR-004
- **Dependências:** T003
- **Trabalho esperado:** implementar o adapter PDFBox 3 que lê cada
  `InputStream`, extrai texto por página e produz conteúdo normalizado, removendo
  apenas espaços/quebras redundantes sem alterar fatos. Preservar nome do
  arquivo e número da página em cada resultado de extração. Ignorar páginas que
  não produzam texto útil, preservando sua numeração para fins de rastreabilidade
  quando aplicável. Falhar de forma clara se o PDF não puder ser lido ou se o
  documento inteiro não produzir nenhum texto útil.
- **Validação:** testes sobre os PDFs canônicos confirmam extração por página e
  presença de texto esperado; testes de fixture cobrem recurso ilegível e texto
  vazio.
- **Critério de conclusão:** cada trecho extraído é rastreável ao PDF e página
  reais que o originaram.

### [ ] T005 — Segmentar conteúdo preservando página, contexto e identidade estável

- **Prioridade:** P0
- **Requisitos:** FR-004, FR-005, FR-006, FR-010, AI-005
- **Dependências:** T004
- **Trabalho esperado:** implementar o chunker com alvo de 450 caracteres e
  sobreposição de 100, sem cruzar limites de página; preferir blocos/linhas e
  frases antes do corte forçado. Gerar `KnowledgeChunk` com ID estável
  `arquivo:página:ordinal`, texto normalizado, nome do arquivo, página e
  ordinal.
- **Validação:** testes verificam limites de página, sobreposição, preferência
  por fronteiras naturais, fallback no limite e estabilidade de IDs/metadados.
- **Critério de conclusão:** o corpus extraído passa a ser uma coleção de
  chunks recuperáveis, íntegros e auditáveis.

### [ ] T006 — Orquestrar ingestão no startup com falha atômica

- **Prioridade:** P0
- **Requisitos:** FR-002 a FR-006, DEP-001, NFR-004
- **Dependências:** T003, T004, T005
- **Trabalho esperado:** criar o caso de uso de ingestão e um `ApplicationRunner`
  que o executa no startup. Fazer a ingestão rejeitar inicialização quando não
  houver PDF, algum recurso não puder ser lido ou não produzir texto/chunks;
  registrar apenas contagens e nomes de arquivos. Preparar a publicação de uma
  nova coleção de chunks para ser atômica quando conectada ao store.
- **Validação:** testes do caso de uso cobrem os quatro PDFs, catálogo vazio,
  falha de leitura e documento sem texto; teste de contexto confirma que o
  runner chama a ingestão durante a inicialização.
- **Critério de conclusão:** ao iniciar, a aplicação só avança com uma base de
  conhecimento válida, completa e rastreável.

**Ponto natural de commit (opcional):** `feat: ingest canonical event PDFs` após T003–T006.

---

## 3. Chunks → BM25 → contexto recuperado

### [ ] T007 — Construir o store BM25 em memória com snapshot imutável

- **Prioridade:** P0
- **Requisitos:** FR-005 a FR-007, NFR-005
- **Dependências:** T002, T005, T006
- **Trabalho esperado:** implementar `InMemoryBm25KnowledgeStore` com
  substituição atômica por snapshot imutável após uma ingestão bem-sucedida.
  Calcular term frequency por chunk, document frequency dos termos sobre o
  conjunto de chunks e tamanho médio dos chunks. Para o cálculo BM25, cada
  `KnowledgeChunk` é tratado como a unidade documental ranqueável. Os metadados
  do PDF de origem permanecem independentes e servem para rastreabilidade e
  atribuição de fontes. A análise lexical deve ser determinística e reutilizável
  pela indexação e pelas consultas, com minúsculas, remoção de diacríticos apenas
  para busca, tokens Unicode alfanuméricos, stopwords comuns do português e
  normalização morfológica leve. Não introduzir embeddings, banco ou mecanismo
  externo.
- **Validação:** testes determinísticos validam tokenização, estatísticas BM25,
  substituição atômica e preservação do texto/origem original para exibição.
- **Critério de conclusão:** existe uma base em memória consistente e pronta
  para ranquear os chunks da ingestão mais recente.

### [ ] T008 — Implementar recuperação ranqueada e seus contratos de ausência

- **Prioridade:** P0
- **Requisitos:** FR-007, FR-009, AI-001, AI-002, NFR-005
- **Dependências:** T007
- **Trabalho esperado:** implementar a consulta BM25 que devolve no máximo cinco
  chunks de score positivo e desempata por arquivo, página e ordinal. Explicitar
  resultado vazio como ausência de evidência recuperada, sem chamar gerador de
  resposta. Integrar o store ao caso de uso de ingestão para que o snapshot só
  seja publicado após o processamento completo.
- **Validação:** testes cobrem top-k, exclusão de score não positivo, desempate
  determinístico, consulta sem resultado e manutenção do snapshot anterior se
  a nova ingestão falhar.
- **Critério de conclusão:** uma pergunta pode produzir contexto limitado,
  ordenado e determinístico ou ausência explícita de contexto.

### [ ] T009 — Fixar a matriz de avaliação de recuperação dos PDFs canônicos

- **Prioridade:** P0
- **Requisitos:** FR-007, AI-001, AI-002, DoD — qualidade de recuperação validada
- **Dependências:** T006, T008
- **Trabalho esperado:** codificar testes para as 12 perguntas e evidências da
  matriz definida no plano. Para cada caso positivo obrigatório, verificar que a
  evidência esperada aparece em algum dos cinco chunks retornados. Para o caso
  não respondível sobre transmissão ao vivo, registrar os chunks recuperados para
  inspeção, mas não exigir resultado vazio do Retriever. A insuficiência factual
  será validada posteriormente no fluxo de question answering, pois retrieval de
  conteúdo relacionado não constitui evidência suficiente para responder
  afirmativamente.
- **Validação:** a suíte calcula e comprova `HitRate@5 = 100%` nos 11 casos
  positivos obrigatórios. O caso 12 não participa do cálculo de `HitRate@5`; ele
  funciona como controle negativo e sua avaliação end-to-end ocorrerá em T013.
- **Critério de conclusão:** há evidência automatizada de que a estratégia BM25
  aprovada recupera contexto suficiente para os casos respondíveis do conjunto de
  perguntas do MVP, sem exigir que perguntas não respondíveis produzam resultado
  vazio no Retriever.

**Ponto natural de commit (opcional):** `feat: add in-memory bm25 retrieval` após T007–T009.

---

## 4. Contexto recuperado → LLM → resposta fundamentada

### [ ] T010 — Implementar o caso de uso de resposta e a regra determinística de insuficiência

- **Prioridade:** P0
- **Requisitos:** FR-001, FR-007 a FR-010, AI-001, AI-002, AI-004 a AI-006, VAL-001, NFR-002
- **Dependências:** T002, T008
- **Trabalho esperado:** implementar `AnswerEventQuestion`: validar a pergunta
  antes de buscar, recuperar contexto e devolver diretamente a mensagem fixa
  “Não encontrei essa informação nos documentos disponíveis sobre o evento.”
  com fontes vazias quando não houver chunks. O caso de uso deve delegar apenas
  perguntas com contexto ao port `AnswerGenerator` e projetar fontes a partir
  dos metadados locais, nunca de texto inventado pelo modelo.
- **Validação:** testes com fake do port confirmam que pergunta vazia não chama
  o gerador, que ausência de chunks não o chama e que a resposta de ausência é
  pt-BR, fixa e sem fontes.
- **Critério de conclusão:** o núcleo protege deterministicamente os cenários
  de entrada inválida e falta de evidência antes da fronteira de IA.

### [ ] T011 — Integrar a Responses API da OpenAI por adapter isolado e estruturado

- **Prioridade:** P0
- **Requisitos:** FR-008, AI-001 a AI-004, AI-006, SEC-001, SEC-002, SEC-005, SEC-007, NFR-002 a NFR-005
- **Dependências:** T001, T002, T010
- **Trabalho esperado:** implementar `OpenAiResponsesAnswerGenerator` com
  `RestClient`, timeout configurável, modelo configurável e chave somente por
  ambiente. Enviar os chunks recuperados como dados não confiáveis e um prompt
  conciso que imponha grounding, pt-BR, resposta direta e insuficiência quando
  aplicável. Solicitar o JSON Schema do plano via Structured Outputs; se o
  modelo configurado não suportar o recurso, falhar claramente na configuração
  ou startup, sem fallback silencioso para JSON guiado apenas por prompt.
- **Validação:** testes com servidor HTTP simulado verificam método/corpo do
  request, schema, timeout e mapeamento da resposta; fixture com prompt
  injection confirma que conteúdo recuperado é entregue como dado, não como
  instrução de sistema.
- **Critério de conclusão:** a única integração com OpenAI fica fora do núcleo,
  usa contrato estruturado e não expõe segredos nos logs ou artefatos.

### [ ] T012 — Validar a saída do modelo e projetar fontes confiáveis

- **Prioridade:** P0
- **Requisitos:** FR-008 a FR-010, AI-001, AI-002, AI-005, VAL-003, SEC-005, SEC-007
- **Dependências:** T010, T011
- **Trabalho esperado:** validar o contrato estruturado retornado pelo modelo e aplicar
  as invariantes correspondentes ao `status`:
    
  - para `ANSWERED`, exigir `answer` não vazio e pelo menos um
    `evidenceChunkId` válido;
  - para `INSUFFICIENT_INFORMATION`, exigir `evidenceChunkIds` vazio.
    Qualquer conteúdo eventualmente retornado no campo `answer` deve ser
    ignorado pela aplicação e substituído pela mensagem fixa:
    
    > “Não encontrei essa informação nos documentos disponíveis sobre o evento.”
    
  Todo `evidenceChunkId` informado pelo modelo em uma resposta `ANSWERED` deve
  ser único e pertencer estritamente ao conjunto de chunks enviado como contexto.
  
  Para `ANSWERED`, construir as fontes únicas e ordenadas exclusivamente a partir
  dos metadados locais dos chunks indicados pelo modelo.
  
  Para `INSUFFICIENT_INFORMATION`, retornar sempre a mensagem fixa da aplicação
  e nenhuma fonte, independentemente do conteúdo eventualmente recebido no campo
  `answer`.
  
  Payload malformado, `status` inválido, evidência inválida, timeout ou falha do
  provedor devem ser convertidos em erro de indisponibilidade, sem fabricar
  resposta factual alternativa.

- **Validação:** testes cobrem, no mínimo:
  
  - `ANSWERED` com resposta não vazia e ao menos um `evidenceChunkId` válido;
  - rejeição de `ANSWERED` com resposta vazia;
  - rejeição de `ANSWERED` sem evidência;
  - rejeição de `ANSWERED` com `evidenceChunkId` inexistente ou fora do contexto enviado;
  - rejeição de IDs duplicados;
  - `INSUFFICIENT_INFORMATION` com `evidenceChunkIds` vazio;
  - descarte de qualquer conteúdo recebido em `answer` quando o status for
    `INSUFFICIENT_INFORMATION`;
  - conversão de `INSUFFICIENT_INFORMATION` para a mensagem fixa em pt-BR e
    fontes vazias;
  - rejeição de `INSUFFICIENT_INFORMATION` contendo `evidenceChunkIds`;
  - fontes deduplicadas e ordenadas para respostas `ANSWERED` válidas;
  - payload malformado, timeout e falha do provedor.
  
- **Critério de conclusão:** nenhuma resposta `ANSWERED` chega ao usuário sem
  pelo menos uma evidência válida pertencente ao contexto enviado ao modelo;
  respostas `INSUFFICIENT_INFORMATION` são normalizadas deterministicamente pela
  aplicação para a mensagem fixa e fontes vazias; e falhas ou violações do
  contrato nunca são convertidas em respostas factuais alternativas.
  
### [ ] T013 — Executar smoke tests reais de IA em perfil separado

- **Prioridade:** P0
- **Requisitos:** FR-008, AI-001, AI-003, AI-006, SEC-001, SEC-002, SEC-005, SEC-007, NFR-004
- **Dependências:** T011, T012
- **Trabalho esperado:** criar um perfil de teste não padrão, acionado somente quando houver
  chave real fornecida externamente, para exercitar a integração com a OpenAI sem tornar acesso
  à rede ou credenciais pré-requisitos da suíte normal.
  
  O perfil deve executar pelo menos três smoke tests reais:
  
  1. **Resposta grounded normal:** enviar uma pergunta documentada juntamente com contexto
     que contenha evidência factual suficiente e confirmar que o provedor retorna uma resposta
     estruturada válida em pt-BR, com `status=ANSWERED` e pelo menos um `evidenceChunkId`
     pertencente ao contexto enviado.
     
  2. **Informação insuficiente com contexto relacionado:** validar a regra geral de que
     conteúdo relacionado ao tema da pergunta não constitui, por si só, evidência factual
     suficiente para sustentar uma resposta.
     
     Como caso de referência do MVP, executar a pergunta canônica sobre transmissão ao vivo
     utilizando o contexto recuperado dos PDFs. Ainda que o retrieval retorne chunks
     relacionados ao tema, incluindo informações sobre gravações de sessões, o modelo deve
     responder com base apenas no que estiver efetivamente sustentado pelo contexto.
     
     Como os documentos não fornecem evidência suficiente para afirmar a existência de
     transmissão ao vivo, o resultado esperado para esse caso de referência é
     `INSUFFICIENT_INFORMATION`.
     
     Esse cenário valida uma propriedade geral de grounding. Sua implementação não deve
     introduzir condicionais, regras, palavras-chave, respostas pré-definidas ou qualquer
     outro tratamento especial baseado na pergunta sobre transmissão ao vivo, em referências
     a gravações ou em fatos específicos do dataset de avaliação.
     
  3. **Prompt injection em conteúdo recuperado:** enviar ao modelo um `KnowledgeChunk` sintético
     e controlado contendo texto que tente alterar o comportamento do assistente, por exemplo,
     instruindo-o a ignorar regras anteriores ou fornecer uma resposta específica. Esse conteúdo
     deve ser enviado pelo mesmo mecanismo usado para contexto recuperado, portanto tratado como
     dado não confiável e não como instrução de sistema.
     
     O caso de teste deve ser construído de forma que o conteúdo legítimo fornecido não seja
     suficiente para responder factual e afirmativamente à pergunta. O comportamento esperado é
     que o modelo preserve as instruções da aplicação e retorne `INSUFFICIENT_INFORMATION`,
     em vez de obedecer à instrução maliciosa presente no chunk.
  
  O fixture de prompt injection deve ser exclusivo dos testes e não deve ser incorporado aos
  quatro PDFs canônicos do MVP.
  
  A suíte padrão deve continuar totalmente determinística e executável sem chamada à OpenAI.

- **Validação:** sob perfil explícito e com credencial fornecida externamente:
  
  * o smoke test grounded retorna Structured Output válido;
  * a resposta normal é apresentada em pt-BR;
  * `ANSWERED` contém evidência válida pertencente ao contexto enviado;
  * o cenário de informação insuficiente confirma que contexto apenas relacionado
    não é tratado como evidência suficiente para sustentar uma resposta factual;
    no caso de referência sobre transmissão ao vivo, o resultado é
    `INSUFFICIENT_INFORMATION` sem qualquer lógica especial baseada nos termos
    ou fatos específicos da pergunta de avaliação;
  * o fixture de prompt injection permanece tratado como conteúdo recuperado não confiável;
  * a instrução maliciosa presente no fixture não altera as regras configuradas do assistente;
  * o caso de prompt injection retorna `INSUFFICIENT_INFORMATION` quando o contexto legítimo
    não contém informação suficiente;
  * a aplicação não utiliza como resposta factual o conteúdo solicitado pela instrução maliciosa;
  * nenhuma credencial é registrada em logs, mensagens de teste ou artefatos produzidos;
  * sem o perfil explícito, nenhum dos smoke tests reais é executado.
  
- **Critério de conclusão:** existe uma verificação opt-in e reproduzível contra o provedor real
  demonstrando tanto o funcionamento do fluxo grounded quanto a preservação das regras da aplicação
  diante de uma tentativa controlada de prompt injection em conteúdo recuperado, sem tornar rede ou 
  credenciais requisitos do build e da suíte padrão.

**Ponto natural de commit (opcional):** `feat: generate grounded answers through openai responses api` após T010–T012. T013 pode seguir em `test: add opt-in OpenAI smoke tests`.

---

## 5. Resposta fundamentada → HTTP/UI

### [ ] T014 — Expor a API HTTP de perguntas com erros seguros em pt-BR

- **Prioridade:** P0
- **Requisitos:** FR-001, FR-009, FR-011, VAL-001 a VAL-003, AI-006, SEC-006, NFR-006
- **Dependências:** T010, T012
- **Trabalho esperado:** criar `POST /api/questions` e seus DTOs em
  `delivery/http`; aceitar `{ "question": "..." }`, impor o limite configurado
  de até 500 caracteres e devolver resposta e fontes no contrato aprovado.
  Mapear entrada ausente/em branco/acima do limite para `400` com código e
  mensagem em pt-BR, e indisponibilidade do LLM para `503` sem detalhes internos.
- **Validação:** testes de controller cobrem resposta válida, ausência de
  evidência com `200`/fontes vazias, três formas de entrada inválida e falha do
  provider em `503` sem stack trace.
- **Critério de conclusão:** o fluxo de pergunta fica acessível por HTTP com
  validação, contrato estável e mensagens compreensíveis em pt-BR.

### [ ] T015 — Servir somente documentos canônicos autorizados como fontes

- **Prioridade:** P0
- **Requisitos:** FR-010, FR-012, AI-005, SEC-005, SEC-006
- **Dependências:** T003, T014
- **Trabalho esperado:** implementar `GET /documents/{filename}` consultando o
  catálogo carregado; servir apenas PDFs conhecidos e retornar `404` para nome
  desconhecido ou tentativa de caminho arbitrário. Associar `url` de fonte ao
  nome de documento validado no resultado da API.
- **Validação:** testes verificam download de cada documento permitido e `404`
  para arquivo inexistente, nomes com traversal e extensões não catalogadas.
- **Critério de conclusão:** cada fonte exposta pela API pode ser aberta sem
  ampliar a superfície de acesso a arquivos.

### [ ] T016 — Entregar a página estática same-origin em português

- **Prioridade:** P0
- **Requisitos:** FR-012, AI-006, NFR-006, cenário 6
- **Dependências:** T014, T015
- **Trabalho esperado:** criar HTML, CSS e JavaScript puro em
  `src/main/resources/static/` com campo/pergunta, envio via `fetch`, estado de
  carregamento, resposta, lista de fontes clicáveis e estados de erro. Renderizar
  dados da API com `textContent`, sem framework, CORS adicional ou injeção de
  HTML retornado pelo modelo.
- **Validação:** teste/smoke local verifica o carregamento da página, envio de
  pergunta, exibição de resposta/fontes, carregamento e erro; inspeção confirma
  textos de interface em pt-BR e uso de `textContent` para dados externos.
- **Critério de conclusão:** um participante consegue executar toda a jornada
  principal pelo navegador na mesma origem da API.

### [ ] T017 — Validar o fluxo integrado local e a superfície operacional mínima

- **Prioridade:** P0
- **Requisitos:** FR-002 a FR-012, DEP-001, VAL-003, NFR-004
- **Dependências:** T006, T009, T012, T014, T015, T016
- **Trabalho esperado:** executar a aplicação empacotada com configuração local
  externa e validar startup com ingestão, `POST /api/questions`, abertura de
  fonte permitida, página estática e `GET /actuator/health`. Consolidar testes
  de integração que liguem controller, caso de uso, recuperação e fake do
  `AnswerGenerator`, preservando os testes reais de IA fora da suíte padrão.
- **Validação:** `mvnw.cmd test` e `mvnw.cmd package` passam; smoke local
  confirma saúde `UP`, cenário respondível, insuficiência sem fonte e interface
  em pt-BR.
- **Critério de conclusão:** o caminho completo pode ser demonstrado localmente
  sem serviços OCI, banco ou chamada obrigatória à OpenAI.

**Ponto natural de commit (opcional):** `feat: expose event knowledge through web api and ui` após T014–T017.

---

## 6. HTTP/UI → diagnóstico → container → OCI → entrega

### [ ] T018 — Adicionar diagnostic logging ao pipeline de retrieval e QA

- **Prioridade:** P0
- **Requisitos:** SEC-001, SEC-005, SEC-007, NFR-004, NFR-005
- **Dependências:** T017
- **Trabalho esperado:** adicionar logging estruturado e seguro, usando somente
  as capacidades já disponíveis de Spring Boot/SLF4J/Logback, sem introduzir
  tecnologia ou infraestrutura adicional de observability. No nível `INFO`,
  registrar o ciclo da consulta, a quantidade de chunks recuperados, o outcome
  do retrieval/QA e os tempos de processamento relevantes. No nível `DEBUG`,
  registrar para cada candidato recuperado o rank, score, `chunkId`, documento,
  página e um pequeno snippet limitado do conteúdo. O logging deve ser
  somente diagnóstico e não pode alterar o comportamento funcional do
  retrieval ou QA.

  Aplicar limites e filtros para nunca registrar segredos, headers de
  autenticação, prompts completos, respostas HTTP brutas ou conteúdo integral
  dos chunks/documentos. O desenho deve evitar que mensagens de exceção ou
  valores de configuração exponham credenciais.
- **Validação:** testes ou inspeção verificam eventos `INFO` para início/fim da
  consulta, quantidade de chunks, outcome e durações; eventos `DEBUG` contêm
  os metadados e snippet limitado dos candidatos; e nenhum log contém chave,
  header de autenticação, prompt completo, resposta HTTP bruta ou texto
  integral. A mesma entrada produz o mesmo resultado funcional com logging
  habilitado ou desabilitado.
- **Critério de conclusão:** o pipeline de retrieval e QA possui diagnóstico
  suficiente para acompanhar consulta, candidatos, resultado e latência sem
  alterar o contrato, a ordenação, a resposta ou as garantias de grounding.

### [ ] T019 — Melhorar a normalização lexical em português no retrieval BM25

- **Prioridade:** P0
- **Requisitos:** FR-007, FR-008, AI-001, AI-002, NFR-004
- **Dependências:** T009, T018
- **Trabalho esperado:** revisar exclusivamente a análise lexical do retrieval
  BM25 textual em memória para tratar stopwords comuns do português e aplicar
  stemming ou uma normalização morfológica leve apropriada para português.
  Garantir que exatamente a mesma estratégia determinística de tokenização,
  remoção de stopwords e normalização seja usada na indexação dos chunks e nas
  consultas. Preservar integralmente o texto original dos `KnowledgeChunk` para
  contexto enviado ao LLM, exibição, snippets e fontes.

  Manter `top-k=5` e o retrieval como BM25 textual em memória. Não introduzir
  embeddings, banco vetorial, busca semântica, serviços externos, dicionários ou
  regras específicas para fatos, documentos ou perguntas do corpus. Não criar
  tratamentos especiais de equivalência de termos ou condicionais baseadas nas
  perguntas exploratórias. Não alterar o comportamento funcional do QA,
  `AnswerGenerator`, Structured Outputs ou regras de grounding. Caso stopwords
  e normalização morfológica geral não sejam suficientes para algum cenário,
  reportar a limitação residual, sem introduzir equivalências, regras ou
  condicionais específicas para fazer os testes passarem.
- **Validação:** executar novamente toda a matriz de avaliação existente e
  preservar `HitRate@5 = 100%` nos 11 casos positivos obrigatórios.

  Reexecutar também os seguintes cenários exploratórios identificados durante
  os testes manuais:

  - `Qual é o horário de início do evento?`;
  - `A que horas começa o evento?`;
  - `A que horas se inicia o evento?`;
  - `Que horas inicia o evento?`.

  Para cada cenário, comparar o resultado obtido após a alteração com o
  baseline registrado antes da T019, verificando se o top-5 contém evidência
  factual suficiente para sustentar a resposta, sem exigir rank ou `chunkId`
  específico.

  Registrar quais cenários apresentaram melhoria, quais permaneceram
  inalterados e eventuais mudanças relevantes no ranking. A validação deve
  comprovar que a nova análise lexical não introduziu regressões na matriz
  original nem nos casos negativos existentes.

  Caso algum dos cenários exploratórios continue sem recuperar evidência
  suficiente, registrar essa limitação residual em vez de introduzir regras,
  equivalências de termos, condicionais ou qualquer outro tratamento específico
  destinado apenas a fazer o cenário passar.

  A suíte completa de testes deve permanecer passando após a alteração.
- **Critério de conclusão:** a melhoria aumenta de forma mensurável a robustez
  lexical do retrieval para perguntas naturais em português, preservando
  `HitRate@5 = 100%` na matriz original, sem alterar o texto original dos chunks
  e sem modificar o contrato funcional do QA. Os cenários exploratórios
  identificados devem ser reexecutados para medir o efeito da mudança; eventuais
  limitações residuais devem ser registradas, sem introduzir regras,
  equivalências ou condicionais específicos para forçar resultados. Esta tarefa
  representa a revisão consciente da decisão anterior de não usar stemming,
  motivada pela evidência reproduzível dos testes exploratórios e diagnostic logs.

### [ ] T020 — Empacotar a aplicação em imagem Docker única

- **Prioridade:** P0
- **Requisitos:** DEP-001, DEP-004, NFR-004, NFR-005
- **Dependências:** T019
- **Trabalho esperado:** criar `Dockerfile` para construir/executar o JAR com
  os PDFs empacotados, porta 8080 e configuração exclusivamente por ambiente.
  Não incluir `.env`, chaves, caminhos da máquina de desenvolvimento, banco ou
  serviços auxiliares na imagem.
- **Validação:** build da imagem e execução local com arquivo externo de
  variáveis confirmam `/actuator/health` e a página; inspeção da imagem/contexto
  confirma ausência de segredo.
- **Critério de conclusão:** existe um único artefato de execução reproduzível
  para o MVP, capaz de iniciar com os PDFs dentro do JAR.

### [ ] T021 — Publicar e validar o MVP na OCI Compute

- **Prioridade:** P0
- **Requisitos:** FR-013, DEP-002 a DEP-004, cenário 6
- **Dependências:** T020
- **Trabalho esperado:** provisionar a VM Linux mínima em subnet pública, IP
  público, Internet Gateway, entrada TCP 8080 e SSH restrito ao IP
  administrativo; executar o único container com `--restart unless-stopped`.
  Manter as variáveis de runtime em
  `/opt/event-knowledge-bot/.env` protegido e fora do repositório. Não criar
  banco, Object Storage, serviços gerenciados, Kubernetes ou infraestrutura
  adicional.
- **Validação:** a partir de rede externa, confirmar URL pública, healthcheck,
  interface em pt-BR, resposta para uma pergunta documentada, fontes abríveis e
  cenário de insuficiência; capturar evidências para submissão sem revelar
  segredos.
- **Critério de conclusão:** o avaliador pode acessar por URL pública uma
  aplicação funcional que percorre o fluxo completo de pergunta até resposta
  fundamentada.

### [ ] T022 — Finalizar README, evidências e checklist de repositório para entrega

- **Prioridade:** P0
- **Requisitos:** REP-001 a REP-004, README-001 a README-008, DEP-005, DoD
- **Dependências:** T017, T021
- **Trabalho esperado:** atualizar o README com visão do problema, arquitetura
  real, tecnologias, execução local, configuração por placeholders, corpus
  canônico, perguntas e respostas reais em pt-BR, fontes, teste, imagem Docker,
  deploy OCI e URL pública. Confirmar que a documentação não descreve recursos
  inexistentes, que o repositório público não contém segredos e que o histórico
  contém incrementos significativos correspondentes aos marcos concluídos.
- **Validação:** revisão cruzada README/implementação/deploy; varredura de
  arquivos versionados para credenciais; abertura da URL documentada e
  conferência de exemplos reais; conferência manual de histórico incremental.
- **Critério de conclusão:** outra pessoa consegue entender, executar e avaliar
  a solução a partir do repositório público, do README e da URL sem acesso a
  informações privadas.

**Ponto natural de commit (opcional):** `build: containerize event knowledge bot` após T020; `docs: document OCI deployment and public MVP` após T021–T022.

---

## MVP Delivery Gate

Considerar a entrega pronta somente quando todas as condições abaixo estiverem
atendidas e houver evidência registrada para cada uma:

- [ ] `mvnw.cmd test` e `mvnw.cmd package` passam no estado final.
- [ ] O startup carrega exclusivamente os quatro PDFs canônicos por classpath,
  extrai texto página a página, segmenta-o e publica um snapshot BM25 em
  memória; falhas de ingestão impedem uma inicialização válida.
- [ ] A matriz de 12 perguntas do plano comprova `HitRate@5 = 100%` nos 11
  casos positivos; o caso de controle negativo é validado no fluxo real de
  question answering e confirma que contexto apenas relacionado ao tema não é
  tratado como evidência factual suficiente, resultando em
  `INSUFFICIENT_INFORMATION` quando a informação necessária não está sustentada
  pelos documentos.
- [ ] O retrieval BM25 mantém `HitRate@5 = 100%` na matriz original e os quatro
  cenários exploratórios de horário são reexecutados após a melhoria lexical,
  com seus resultados comparados ao baseline e eventuais limitações residuais
  registradas. A alteração não introduz regressões na matriz original nem
  regras, equivalências ou condicionais específicos para forçar resultados,
  mantendo top-k=5 e preservando o texto original dos chunks.
- [ ] Perguntas válidas percorrem recuperação, contexto e OpenAI Responses API
  com Structured Outputs; a saída é validada e só expõe fontes derivadas de
  `evidenceChunkIds` pertencentes ao contexto enviado.
- [ ] Pergunta em branco, longa ou ausente retorna `400` em pt-BR e não chama o
  gerador; ausência de evidência retorna a mensagem fixa em pt-BR, `200` e
  `sources: []`; indisponibilidade do provedor retorna `503` sem detalhes
  internos.
- [ ] `POST /api/questions`, `GET /documents/{filename}` para PDFs autorizados,
  `404` para nomes não autorizados e `GET /actuator/health` funcionam conforme
  o contrato.
- [ ] A página same-origin em pt-BR permite perguntar, aguardar, ler resposta,
  abrir fontes e entender erros sem expor detalhes técnicos.
- [ ] Não há credenciais reais no repositório, imagem ou logs; a chave é
  fornecida externamente e o `.env` de produção fica protegido na VM.
- [ ] A imagem Docker única inicia com os PDFs empacotados e a aplicação está
  publicamente acessível na OCI pela URL documentada, com saúde e jornada
  principal validadas externamente.
- [ ] O README descreve somente a implementação real, inclui arquitetura,
  configuração, execução, exemplos reais, deploy e URL pública; o repositório
  público apresenta histórico incremental significativo e evidências de deploy.
