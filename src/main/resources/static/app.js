/**
 * MindCache 前端通用工具。
 * 所有页面共享 API 调用、Toast 等基础设施。
 */
const MindCache = (() => {

    const BASE = "/api/v1";

    // ---- Toast ----
    let toastTimer = null;
    const toast = document.createElement("div");
    toast.className = "toast";
    document.body.appendChild(toast);

    function showToast(msg, isError = false) {
        toast.textContent = msg;
        toast.className = "toast " + (isError ? "error" : "") + " show";
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => {
            toast.classList.remove("show");
        }, 3000);
    }

    // ---- API 调用 ----
    async function apiCall(url, options = {}) {
        try {
            const resp = await fetch(BASE + url, {
                headers: { "Content-Type": "application/json", ...options.headers },
                ...options,
            });
            const json = await resp.json();
            if (!resp.ok || json.code !== 200) {
                throw new Error(json.message || `HTTP ${resp.status}`);
            }
            return json.data;
        } catch (err) {
            showToast(err.message, true);
            throw err;
        }
    }

    /**
     * 上传文件（multipart/form-data），用于语音/图片。
     */
    async function uploadFile(url, file) {
        const formData = new FormData();
        formData.append("file", file);
        try {
            const resp = await fetch(BASE + url, {
                method: "POST",
                body: formData,
            });
            const json = await resp.json();
            if (!resp.ok || json.code !== 200) {
                throw new Error(json.message || `HTTP ${resp.status}`);
            }
            return json.data;
        } catch (err) {
            showToast(err.message, true);
            throw err;
        }
    }

    return { showToast, apiCall, uploadFile };

})();
