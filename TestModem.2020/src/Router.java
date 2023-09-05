import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.regex.Pattern;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.net.*;
import java.util.zip.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Router extends Modem {

	public static final AtomicBoolean staticRouter = new AtomicBoolean();
	public static final AtomicBoolean connected = new AtomicBoolean();
	protected String cmd = "";
	public static DatagramSocket w;
	private static final Map<String, Long> lastSends = new ConcurrentHashMap<>();
	private static Settings sets;

	public Router(final String imei, final int port, final Settings settings, final Logger logger) {
		super(imei, port, settings, logger);
		sets = settings;
	}

	@Override
	public void secondConnect() {
		String[] hm = helloMessage(false).split("\n");
		lastSend = System.currentTimeMillis();
		lastSends.put(imei, lastSend);

		if (!staticRouter.compareAndSet(false, true)) {
			for (String h : hm) 
				try {
					send(imei, h);
				} catch (IOException | InterruptedException e) {
					System.out.println("err0: " + e.getMessage());
					log("ROUTER: SOCKET FAILURE");
					break;
				}
			return;
		}

		while (true) {
			connected.set(false);
			try {

				Thread t = new Thread(() -> {
					try {
						while (!connected.get()) {
							Thread.sleep(5000);

							for (Map.Entry<String, Long> im : lastSends.entrySet()) {
								if (settings.getKeepTime() * 10 < System.currentTimeMillis() - im.getValue()) {
									String mes = "{\"type\": \"ping\", \"data\": {}}";
									send(im.getKey(), mes);
								}
							}

						}
					} catch (IOException | InterruptedException e) {
						System.out.println("err1: " + e.getMessage());
						log("ROUTER: SECOND CONNECTION FAILURE. SOCKET ANSWERED: " + e.getMessage());
						connected.set(true);
					}
				});
				
				t.start();

				try {

					for (String im : lastSends.keySet()) {
						for (String h : hm) 
							send(im, h);
					}

					while (!connected.get()) {
						Thread.sleep(5 * 1000);
					}
				} catch (InterruptedException e) {
					System.out.println("err2: " + e.getMessage());
					log("ROUTER: PROCESS INTERRUPTED");
					break;//interrupt
				}

				t.interrupt();
			} catch (IOException e) {
				System.out.println("err3: " + e.getMessage());
				log("ROUTER: SOCKET FAILURE");
				break;
			}
		}
	}

	public static void sendCommand(String imei, String s) throws IOException, InterruptedException {
		send(imei, s);
	}

	@Override
	protected String helloMessage(final boolean modeEncapsulation) {
		return "{\"type\": \"board\", \"data\": {\"version\": \"" + sets.getVer() + "\", \"hostname\": \"iRZ-Router\", \"model\": \"" + sets.getDev() + "\", \"kernel\": \"" + sets.getBld() + "\", \"ram\": \"75.01Mb/122.06Mb\", \"uptime\": \"00h 09m 36s\", \"localtime\": \"2023-02-03 11:06:56 (GMT+3)\", \"remember\": \"TEST\"}}\n" +
				"{\"type\": \"routing\", \"data\": [{\"interfaces\": [\"sim" + sets.getSim() + "\"], \"mode\": \"backup\"}, {\"metric\": \"3\", \"target\": \"0.0.0.0/0\", \"interface\": \"sim" + sets.getSim() + "\"}, {\"metric\": \"103\", \"target\": \"10.152.29.212/30\", \"interface\": \"sim" + sets.getSim() + "\"}, {\"metric\": \"103\", \"target\": \"10.152.29.214/32\", \"interface\": \"sim" + sets.getSim() + "\"}, {\"metric\": \"0\", \"target\": \"192.168.1.0/24\", \"interface\": \"lan\"}]}\n" + 
				"{\"type\": \"interfaces\", \"data\": {\"loopback\": {\"rx_bytes\": \"0.01Mb\", \"proto\": \"static\", \"metric\": \"0\", \"tx_bytes\": \"0.01Mb\", \"device\": \"lo\", \"active\": 1, \"uptime\": \"00h 09m 00s\", \"ipv4\": \"127.0.0.1/8\", \"mac\": \"00:00:00:00:00:00\"}, \"lan\": {\"rx_bytes\": \"0.03Mb\", \"proto\": \"static\", \"metric\": \"0\", \"tx_bytes\": \"0.20Mb\", \"device\": \"br-lan\", \"active\": 1, \"uptime\": \"00h 09m 00s\", \"ipv4\": \"192.168.1.1/24\", \"mac\": \"f0:81:af:02:ac:12\"}, \"sim" + sets.getSim() + "\": {\"rx_bytes\": \"0.01Mb\", \"proto\": \"mobile\", \"metric\": \"103\", \"imei\": \"865546044124887\", \"iccid\": \"89701012417859028663\", \"tx_bytes\": \"0.01Mb\", \"active\": 1, \"module\": \"QUECTEL EC25\", \"csq\": \"" + sets.getCsq() + "\", \"device\": \"sim" + sets.getSim() + "\", \"revision\": \"EC25EUGAR06A03M4G\", \"network\": \"25001\", \"operator\": \"MTS RUS MTS RUS\", \"uptime\": \"00h 07m 19s\", \"mode\": \"" + sets.getTyp() + "\", \"ipv4\": \"10.152.29.213/30\", \"mac\": \"00:00:00:00:00:00\"}}}\n" + 
				"{\"type\": \"status\", \"data\": {\"cpu\": {\"idle\": \"90%\", \"io\": \"0%\", \"irq\": \"0%\", \"nic\": \"0%\", \"sirq\": \"0%\", \"sys\": \"9%\", \"usr\": \"0%\"}, \"memory\": {\"free\": \"74588KiB\", \"total\": \"124992KiB\"}, \"uptime\": \"00h 09m 36s\", \"localtime\": \"2023-02-03 11:06:56 (GMT+3)\"}}";
	}

	private static synchronized void send(String imei, String mes) throws IOException, InterruptedException {
		String s = imei + "@";

		// Process nc = null;
		// String[] cmds = new String[] {"nc", "-u", sets.getHostMainServer(), String.valueOf(sets.getPortMainServer())};
		// ProcessBuilder pb = new ProcessBuilder(cmds);
		// nc = pb.start();
		// nc = Runtime.getRuntime().exec(cmds);

		// try (OutputStream w = nc.getOutputStream()) {
		DatagramSocket w = new DatagramSocket();

			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			baos.write(s.getBytes());

			try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
				gzip.write(mes.getBytes());
			}

			byte[] m = baos.toByteArray();
			baos.close();



			DatagramPacket packet = new DatagramPacket(m, m.length, 
				InetAddress.getByName(sets.getHostMainServer()), sets.getPortMainServer());

			w.send(packet);
			// w.write(m);
			// w.flush();
		// }

		w.close();
		// nc.destroyForcibly();

		lastSends.put(imei, System.currentTimeMillis());
		
		System.out.println("Send [" + imei + "]");

		Thread.sleep(10);
	}
	
}
