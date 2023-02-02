import java.io.*;
import java.net.Socket;
import java.util.Random;
import java.util.regex.Pattern;
import java.lang.Process;
import java.net.*;
import java.util.zip.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Router extends Modem {

	public static final AtomicBoolean connected = new AtomicBoolean();
	private Process nc;
	
	public Router(final String imei, final int port, final Settings settings, final Logger logger) {
		super(imei, port, settings, logger);
	}

	@Override
	public void secondConnect() {
		lastSend = System.currentTimeMillis();
		while (true) {
			connected.set(false);
			try {
				nc = Runtime.getRuntime().exec("nc -u " + settings.getHostMainServer() + " " + settings.getPortMainServer());
				Thread t = new Thread(() -> {
					try (Writer w = new OutputStreamWriter(nc.getOutputStream(), "UTF-8")) {
						while (!connected.get()) {
							while (settings.getKeepTime() > System.currentTimeMillis() - lastSend) {
								Thread.sleep(5000);
							}

							String s = imei + "@";
							String mes = "{v: 20.6}";

							ByteArrayOutputStream baos = new ByteArrayOutputStream();

							try (DeflaterOutputStream gzip = new DeflaterOutputStream(baos)) {
								gzip.write(mes.getBytes(), 0, mes.length());
								gzip.flush();
							}

							byte[] m = baos.toByteArray();
							baos.close();

							w.write(s + new String(m) + "\n");
							w.flush();

							lastSend = System.currentTimeMillis();
							System.out.println("Send " + imei);
						}
					} catch (IOException | InterruptedException e) {
						System.out.println("err1" + e.getMessage());
						log("ROUTER: SECOND CONNECTION FAILURE. SOCKET ANSWERED: " + e.getMessage());
						connected.set(true);
					}
				});
				
				t.start();

				try {
					while (!connected.get()) {
						Thread.sleep(5 * 1000);
					}
				} catch (InterruptedException e) {
					System.out.println("err2" + e.getMessage());
					log("ROUTER: PROCESS INTERRUPTED");
					break;//interrupt
				}

				t.interrupt();

				nc.destroy();
				if (nc.isAlive()) {
				    nc.destroyForcibly();
				}
			} catch (IOException e) {
				System.out.println("err3" + e.getMessage());
				log("ROUTER: SOCKET FAILURE");
				break;
			}
		}
	}
	
}
