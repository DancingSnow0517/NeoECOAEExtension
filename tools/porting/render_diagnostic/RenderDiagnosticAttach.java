import com.sun.tools.attach.VirtualMachine;

public final class RenderDiagnosticAttach {
    public static void main(String[] arguments) throws Exception {
        VirtualMachine client = VirtualMachine.attach(arguments[0]);
        try { client.loadAgent(arguments[1], arguments[2]); }
        finally { client.detach(); }
    }
}
