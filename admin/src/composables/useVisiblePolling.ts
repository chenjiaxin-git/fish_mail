import { onMounted, onUnmounted } from 'vue';
export function useVisiblePolling(load: (silent: boolean) => Promise<void>, interval = 30000): void {
    let timer: ReturnType<typeof setTimeout> | undefined, disposed = false, running = false, pending = false;
    const stop = () => {
        if (timer !== undefined)
            clearTimeout(timer);
        timer = undefined;
    };
    const run = async () => {
        stop();
        if (disposed || document.hidden)
            return;
        if (running) {
            pending = true;
            return;
        }
        running = true;
        try {
            await load(true);
        }
        finally {
            running = false;
            if (!disposed && !document.hidden) {
                if (pending) {
                    pending = false;
                    void run();
                }
                else
                    timer = setTimeout(() => void run(), interval);
            }
        }
    };
    const visible = () => {
        stop();
        if (!document.hidden)
            void run();
    };
    onMounted(() => {
        document.addEventListener('visibilitychange', visible);
        void run();
    });
    onUnmounted(() => {
        disposed = true;
        stop();
        document.removeEventListener('visibilitychange', visible);
    });
}
