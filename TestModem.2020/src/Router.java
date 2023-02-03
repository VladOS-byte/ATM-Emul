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
	protected String cmd = "";
	
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
				final OutputStream w = nc.getOutputStream();
				
				String[] hm = helloMessage(false).split("\n");
				for (String h : hm)
					send(w, h);

				Thread t = new Thread(() -> {
					try {
						while (!connected.get()) {
							while (!this.needUpdateGpio && settings.getKeepTime() > System.currentTimeMillis() - lastSend) {
								Thread.sleep(5000);
							}

							String mes = this.needUpdateGpio ? cmd : "{\"type\": \"ping\", \"data\": {}}";
							this.needUpdateGpio = false;

							send(w, mes);

							lastSend = System.currentTimeMillis();
							System.out.println("Send " + imei);
						}
					} catch (IOException | InterruptedException e) {
						System.out.println("err1: " + e.getMessage());
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
					System.out.println("err2: " + e.getMessage());
					log("ROUTER: PROCESS INTERRUPTED");
					break;//interrupt
				}

				t.interrupt();

				nc.destroy();
				if (nc.isAlive()) {
				    nc.destroyForcibly();
				}
			} catch (IOException e) {
				System.out.println("err3: " + e.getMessage());
				log("ROUTER: SOCKET FAILURE");
				break;
			}
		}
	}

	@Override
	public void sendCommand(String s) {
		this.needUpdateGpio = true;
		this.cmd = s;
	}

	@Override
	protected String helloMessage(final boolean modeEncapsulation) {
		return "{\"type\": \"board\", \"data\": {\"version\": \"200600\", \"hostname\": \"iRZ-Router\", \"model\": \"RT211w\", \"kernel\": \"4.14.162\", \"ram\": \"75.01Mb/122.06Mb\", \"uptime\": \"00h 09m 36s\", \"localtime\": \"2023-02-03 11:06:56 (GMT+3)\", \"remember\": \"TEST\"}}\n" +
				"{\"type\": \"routing\", \"data\": [{\"interfaces\": [\"sim1\"], \"mode\": \"backup\"}, {\"metric\": \"3\", \"target\": \"0.0.0.0/0\", \"interface\": \"sim1\"}, {\"metric\": \"103\", \"target\": \"10.152.29.212/30\", \"interface\": \"sim1\"}, {\"metric\": \"103\", \"target\": \"10.152.29.214/32\", \"interface\": \"sim1\"}, {\"metric\": \"0\", \"target\": \"192.168.1.0/24\", \"interface\": \"lan\"}]}\n" + 
				"{\"type\": \"interfaces\", \"data\": {\"loopback\": {\"rx_bytes\": \"0.01Mb\", \"proto\": \"static\", \"metric\": \"0\", \"tx_bytes\": \"0.01Mb\", \"device\": \"lo\", \"active\": 1, \"uptime\": \"00h 09m 00s\", \"ipv4\": \"127.0.0.1/8\", \"mac\": \"00:00:00:00:00:00\"}, \"lan\": {\"rx_bytes\": \"0.03Mb\", \"proto\": \"static\", \"metric\": \"0\", \"tx_bytes\": \"0.20Mb\", \"device\": \"br-lan\", \"active\": 1, \"uptime\": \"00h 09m 00s\", \"ipv4\": \"192.168.1.1/24\", \"mac\": \"f0:81:af:02:ac:12\"}, \"sim1\": {\"rx_bytes\": \"0.01Mb\", \"proto\": \"mobile\", \"metric\": \"103\", \"imei\": \"865546044124887\", \"iccid\": \"89701012417859028663\", \"tx_bytes\": \"0.01Mb\", \"active\": 1, \"module\": \"QUECTEL EC25\", \"csq\": \"31\", \"device\": \"sim1\", \"revision\": \"EC25EUGAR06A03M4G\", \"network\": \"25001\", \"operator\": \"MTS RUS MTS RUS\", \"uptime\": \"00h 07m 19s\", \"mode\": \"4G\", \"ipv4\": \"10.152.29.213/30\", \"mac\": \"00:00:00:00:00:00\"}}}";
	}

	private void send(OutputStream w, String mes) throws IOException {
		String s = imei + "@";

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
			gzip.write(mes.getBytes());
		}

		byte[] m = baos.toByteArray();
		baos.close();

		w.write(s.getBytes());
		w.write(m);
		w.flush();
	}
	
}
