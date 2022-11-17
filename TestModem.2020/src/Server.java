import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;

public class Server {
	
	private final Random random = new Random();
	private final Sender sender = new Sender();
	private final Logger logger;
	private Settings settings;
	private final String imei;
	private final int portModem;
	
	private byte[] prefix = {(byte) 0xC8, (byte) 0xCA, (byte) 0xCB, (byte) 0xCC, (byte) 0xCD};
	
	private byte[] suffix = {(byte) '\r', (byte) '\n'};
	
	public Server(final String imei, final int portModem, Settings settings, final Logger logger) {
		this.settings = settings;
		this.logger = logger;
		this.imei = imei;
		this.portModem = portModem;
		new Thread(() -> {
				while (true) {
					try {
						Thread.sleep(20000);
					} catch (InterruptedException e) {
						log("MODEM: PROCESS INTERRUPTED");//interrupt
					}
					if (settings.isModeData()) {
						server();
					}
				}
			
		}).start();
	}
	
	private void server() {
		try(Socket socket = new Socket(settings.getHostMainServer(), portModem)) {
			byte[] message = createDataMessage();
			while (!socket.isClosed() && settings.isModeData()) {
				sender.sendMessage(socket, message);
				Thread.sleep(10);
				byte[] data = sender.getByteMessage(socket);
				if (data == null || data.length != message.length) {
					log("SERVER: DATA IS NOT CORRECT: '" + (data == null ? "null" : "" + data.length) + "'" + " != " + message.length);
					break;//?
				} else {
//					log("SERVER: DATA IS CORRECT");
					Thread.sleep((settings.getMinTO() + random.nextInt(settings.getMaxTO() - settings.getMinTO())) * 1000);
				}
			}
		} catch (UnknownHostException e) {
			log("SERVER ERROR: UNKNOWN HOST");
		} catch (IOException e) {
			log("SERVER CONNECTION FAILURE. SOCKET ANSWERED: " + e.getMessage());
		} catch (InterruptedException e) {
			log("SERVER INTERRUPTED");
		}
	}
	
	private byte[] createDataMessage() throws IOException {
		if (settings.isModeEncapsulation()) {
			byte[] data = dataBuild();
			byte inf = (byte)(settings.getDataSize() / 256);
			byte suf = (byte)(settings.getDataSize() % 256);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write(prefix);
			baos.write(0x01);
			baos.write(inf);
			baos.write(suf);
			baos.write(data);
			baos.write(suffix);
			return baos.toByteArray();
		} else {
			return dataBuild();
		}
	}
	
	private byte[] dataBuild() {
		ByteArrayOutputStream dataBuilder = new ByteArrayOutputStream();
		for (int i = 0; i < settings.getDataSize(); i++) {
			dataBuilder.write((byte)0xAB);
		}
		return dataBuilder.toByteArray();
	}
	
	private void log(final String message) {
		logger.log(imei, message);
	}
	
}
