(() => {
    "use strict";

    const form = document.querySelector("#question-form");
    const questionInput = document.querySelector("#question");
    const submitButton = document.querySelector("#submit-button");
    const loading = document.querySelector("#loading");
    const error = document.querySelector("#error");
    const answerSection = document.querySelector("#answer-section");
    const answer = document.querySelector("#answer");
    const sourcesSection = document.querySelector("#sources-section");
    const sources = document.querySelector("#sources");

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const question = questionInput.value.trim();
        if (!question) {
            showError("Digite uma pergunta para continuar.");
            questionInput.focus();
            return;
        }

        setLoading(true);
        clearResult();

        try {
            const response = await fetch("/api/questions", {
                method: "POST",
                headers: { "Content-Type": "application/json", Accept: "application/json" },
                body: JSON.stringify({ question })
            });
            const payload = await readJson(response);

            if (!response.ok) {
                throw new Error(apiErrorMessage(response.status, payload));
            }

            showResult(payload);
        } catch (requestError) {
            showError(requestError instanceof TypeError
                ? "Não foi possível conectar ao serviço. Tente novamente."
                : requestError.message);
        } finally {
            setLoading(false);
        }
    });

    async function readJson(response) {
        try {
            return await response.json();
        } catch {
            return {};
        }
    }

    function apiErrorMessage(status, payload) {
        if (payload && typeof payload.message === "string" && payload.message.trim()) {
            return payload.message;
        }
        if (status === 400) return "A pergunta não é válida. Revise o texto e tente novamente.";
        if (status === 503) return "O serviço de respostas está indisponível no momento. Tente novamente mais tarde.";
        return "Não foi possível obter uma resposta agora. Tente novamente.";
    }

    function setLoading(isLoading) {
        loading.hidden = !isLoading;
        submitButton.disabled = isLoading;
        questionInput.disabled = isLoading;
    }

    function clearResult() {
        error.hidden = true;
        answerSection.hidden = true;
        sourcesSection.hidden = true;
        answer.textContent = "";
        sources.replaceChildren();
    }

    function showResult(payload) {
        answer.textContent = typeof payload.answer === "string" ? payload.answer : "";
        answerSection.hidden = false;

        const sourceList = Array.isArray(payload.sources) ? payload.sources : [];
        sourceList.forEach((source) => {
            const item = document.createElement("li");
            const link = document.createElement("a");
            const documentName = typeof source.documentName === "string" ? source.documentName : "Documento";
            const page = Number.isInteger(source.page) ? source.page : "";
            link.textContent = page ? `${documentName} (página ${page})` : documentName;
            link.href = typeof source.url === "string" ? source.url : "#";
            link.target = "_blank";
            link.rel = "noopener noreferrer";
            item.append(link);
            sources.append(item);
        });
        sourcesSection.hidden = sourceList.length === 0;
    }

    function showError(message) {
        error.textContent = message;
        error.hidden = false;
        answerSection.hidden = true;
        sourcesSection.hidden = true;
    }
})();
