import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Sender {
	
	public long sendMessage(final Socket socket, final String message) throws IOException, InterruptedException {
		return sendMessage(socket, message.getBytes());
	}
	
	public long sendMessage(final Socket socket, final byte[] message) throws IOException, InterruptedException {
		socket.getOutputStream().write(message);
		socket.getOutputStream().flush();
		return System.currentTimeMillis();
	}
	
	public byte[] resizeByteMessage(final byte[] message) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int size = message.length;
		while (size > 0 && message[--size] == '\0');
		for (int i = 0; i <= size; i++) {
			baos.write(message[i]);
		}
		return size < 2 ? null : baos.toByteArray();
	}
	
	public byte[] getByteMessage(final Socket socket) throws IOException {
		byte[] buf = new byte[4096];
		
		socket.getInputStream().read(buf);
		
		return resizeByteMessage(buf);
	}
	
	public String getMessage(final Socket socket) throws IOException {
		byte[] m = getByteMessage(socket);
		return m == null ? null : new String(m);
	}
	
}
