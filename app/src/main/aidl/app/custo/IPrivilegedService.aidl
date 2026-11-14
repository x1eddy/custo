package app.custo;

interface IPrivilegedService {
    // Reserved transaction code Shizuku uses to tear the service down.
    void destroy() = 16777114;

    // Runs `sh -c command` as the shell user. Returns "<exitCode>\n<stdout+stderr>".
    String exec(String command) = 1;

    // Registers (and enables) a fabricated overlay owned by com.android.shell, through the
    // OverlayManager binder. Works without root, unlike `cmd overlay fabricate` on Android 14+.
    // Returns null on success, or an error message.
    String fabricate(String target, String name, String resource, int type, int value) = 2;

    // Unregisters a fabricated overlay previously created by fabricate(). Returns null or an error.
    String unfabricate(String name) = 3;
}
