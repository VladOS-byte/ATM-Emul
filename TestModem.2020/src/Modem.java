import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Random;
import java.util.regex.Pattern;

public class Modem {
	//modem.info
	protected final String imei;
	
	protected final int port;
	//modem.settings
	protected final Settings settings;
	
	protected final Logger logger;
	
	protected final int dataSize;
	
	protected final Random random = new Random();
	protected final Sender sender = new Sender();
	
	protected long lastSend;
	protected long gpioLastSend = 0L;
	
	protected static final byte[] keepAliveMessage = {(byte) 0xB5, (byte) 0xBC, (byte) 0xBD, (byte) 0xBE, (byte) 0xBF};
	
	protected static final byte[] prefix = {(byte) 0xC8, (byte) 0xCA, (byte) 0xCB, (byte) 0xCC, (byte) 0xCD};
	
	protected static final byte[] suffix = {(byte) '\r', (byte) '\n'};
	protected boolean needUpdateGpio = false;
	
	
	public Modem(final String imei, final int port, final Settings settings, final Logger logger) {
		this.imei = imei;
		this.port = port;
		this.settings = settings;
		this.logger = logger;
		
		dataSize = settings.getDataSize();
		
		new Thread(() -> {
/*			try {
				firstConnect();
				Thread.sleep(500);
			} catch (InterruptedException e) {
				log("MODEM: PROCESS INTERRUPTED");
			} catch (IOException e) {
				log("MODEM: FIRST CONNECTION FAILURE. SOCKET ANSWERED: " + e.getMessage());
			}*/
			secondConnect();
		}).start();
	}
	
	public String getImei() {
		return imei;
	}
	
	public int getPort() {
		return port;
	}
	
	private void firstConnect() throws IOException, InterruptedException {
		try (Socket socket = new Socket(settings.getHostMainServer(), settings.getPortMainServer())) {
			lastSend = sender.sendMessage(socket, helloMessage(false));
		} catch (IOException e) {
			throw e;
		}
	}
	
	public void secondConnect() {
		lastSend = System.currentTimeMillis();
		while (true) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				log("MODEM: PROCESS INTERRUPTED");
				break;//interrupt
			}

			try (Socket socket = new Socket(settings.getHostMainServer(), settings.getPortMainServer())) {
				socket.setTcpNoDelay(true);
				lastSend = sender.sendMessage(socket, helloMessage(settings.isModeEncapsulation()));
				
				Thread t = new Thread(() -> {
					try {
						while (!socket.isClosed()) {
							while (settings.getKeepTime() > System.currentTimeMillis() - lastSend) {
								Thread.sleep(5000);

								int timeout = settings.getGpioChangeTimeout() * 1000;
								if (System.currentTimeMillis() - gpioLastSend > timeout && timeout > 0) { 
									needUpdateGpio = true;
									settings.setGpioX(random.nextInt(3) + 1, random.nextInt(3));
								}

								if (needUpdateGpio) {
									gpioLastSend = System.currentTimeMillis();
									doCommand(socket, (byte) 0x02);
									needUpdateGpio = false;
								}
							}

							byte[] mes = settings.isModeEncapsulation() ? 
									createMessage(new byte[] {(byte) 0x02, (byte) 0x00, (byte) 0x05}, keepAliveMessage) : keepAliveMessage;

							if (settings.getKeepTime() != 50_000) {
								byte[] m1 = new byte[10], m2 = new byte[mes.length - 10];
								System.arraycopy(mes, 0, m1, 0, m1.length);
								System.arraycopy(mes, m1.length, m2, 0, m2.length);
								lastSend = sender.sendMessage(socket, m1);
								lastSend = sender.sendMessage(socket, m2);
							} else {
								lastSend = sender.sendMessage(socket, mes);
							}
							
						}

					} catch (IOException | InterruptedException e) {
						log("MODEM: SECOND CONNECTION FAILURE. SOCKET ANSWERED: " + e.getMessage());
						try {
							socket.close();
						} catch (IOException ignored) {}
					}
				});
				
				t.start();
				
				listenAnswer(socket, sender.getMessage(socket));
				
				t.interrupt();
			} catch (NullPointerException e) {
				
			} catch (IOException e) {
				log("MODEM: SECOND CONNECTION FAILURE. SOCKET ANSWERED: " + e.getMessage());
			} catch (InterruptedException e) {
				log("MODEM: PROCESS INTERRUPTED");
				break;//interrupt
			}
		}
		log("out");
	}
	
	private void listenAnswer(final Socket socket, final String firstLog) throws InterruptedException {
		try {
			if (isLegalStartLog(firstLog)) {
				String modeOfAnswer = firstLog.substring(19, 22);
				boolean modeSet = modeOfAnswer.equals("SET");
				if (modeOfAnswer.equals("DAT") || Math.abs(settings.getChannel()) != 1) {
					while (!socket.isClosed()) {
						byte[] data = sender.getByteMessage(socket);
						if (data == null) {
							Thread.sleep(5000);
						} else if (isLegalStartCommand(data)) {
							doCommand(socket, data[10]);
						} else if (settings.isModeData()) {
							sendDataPackage(socket, data);
						}
					}
				} else if (!settings.getPsw().equals(firstLog.substring(27, 31))) {
					lastSend = sender.sendMessage(socket, createMessage(new byte[] {(modeSet ? (byte) 0x05 : (byte) 0x04), 0x00, 0x09, 
							0x50, 0x41, 0x53, 0x53, 0x57, 0x52, 0x4F, 0x4E, 0x47}));//"PASSWRONG"
				} else {
					lastSend = sender.sendMessage(socket, createMessage(new byte[] {(modeSet ? (byte) 0x05 : (byte) 0x04), 0x00, 0x06,
							0x50, 0x41, 0x53, 0x53, 0x4F, 0x4B}));//"PASSOK"
					if (modeSet) {
						setModem(socket);
					} else {
						frmModem(socket);
					}
				}
			} else {
				log("MODEM: IRZ-SERVER ANSWER REJECTED: " + firstLog);
			}
			socket.close();
		} catch (NullPointerException e) {} catch (IOException e) {
			log("MODEM: SECOND CONNECTION FAILURE. SOCKET ANSWERED: " + e.getMessage());
		} finally {
			Thread.sleep(5000);
		}
	}
	
	public void sendGpio() {
		needUpdateGpio = true;
	}

	public void sendCommand(String s) {
		needUpdateGpio = true;
	}
	
	private void doCommand(final Socket socket, final byte command) throws IOException, InterruptedException {
		Pair<byte[], byte[]> answer = null;
		if ((answer = settings.getAnswer(command)) != null) {
			lastSend = sender.sendMessage(socket, createMessage(answer.getKey(), answer.getValue()));
		}
	}
	
	private boolean isLegalStartLog(final String query) {
		return Pattern.matches("[0-9]{4}(0[1-9]|1[0-2])([0-2][0-9]|3[0-1])([0-1][0-9]|2[0-3])([0-5][0-9]){2},"
				+ "MOD=(DAT|SET,(PSW=[0-9]{4})|FRM,(PSW=[0-9]{4}),(PAG=[0-9]{4})),\r", query);
	}
	
	private boolean isLegalStartCommand(final byte[] query) {
		boolean checker = query.length >= 13;
		for (int i = 0; i < query.length; i++) {
			if (i < prefix.length) {
				checker &= (query[i] == prefix[i]);
			} else if (i == prefix.length) {
				checker &= (query[i] == 0x0C);
			} else if (i == prefix.length + 1) {
				checker &= (query[i] == 0x00);
			} else if (i == prefix.length + 2) {
				checker &= (query[i] == 0x03);
//			} else if (i == prefix.length + 3) {
//				checker &= (query[i] == 0x00);
			} else if (i == prefix.length + 4) {
				checker &= (query[i] == 0x40);
			} else if (prefix.length + 5 < i && i <= prefix.length + 7) {
				checker &= (query[i] == suffix[i - prefix.length - 6]);
			}
			if (checker == false) {
				break;
			}
		}
		return checker;
	}
	
	private void sendDataPackage(final Socket socket, final byte[] data) throws IOException, InterruptedException {
		if (data.length != dataSize + (settings.isModeEncapsulation() ? 20 : 0)) {
			log("MODEM: DATA SIZE WRONG: " + data.length + ". EXPECTED SIZE: " + (dataSize + (settings.isModeEncapsulation() ? 20 : 0)));
			Thread.sleep(5000);
		} else {
			lastSend = sender.sendMessage(socket, data);
		}
	}
	
	private void setModem(final Socket socket) throws IOException, InterruptedException, NullPointerException {
		log("SETS");
		if (settings.isModeCheckSet()) {
			checkSetFrmCommand(socket, true);
		}
	}
	
	private void frmModem(final Socket socket) throws IOException, InterruptedException, NullPointerException {
		if (settings.isModeCheckFrm()) {
			checkSetFrmCommand(socket, false);
		}
	}
	
	private void checkSetFrmCommand(final Socket socket, boolean modeSet) throws IOException, InterruptedException, NullPointerException {
		log("SETS starts");
		String data = sender.getMessage(socket);
		int counter = 1;
		while (true) {
			Thread.sleep(50);
			if (data.substring(12, data.length() - 3).replaceAll("\\s+", "_").equals("at$boot_end")) {
				break;
			}
			byte[] cbytes = toBytes(counter);
			lastSend = sender.sendMessage(socket, createMessage(new byte[]{(modeSet ? (byte) 0x05 : (byte) 0x04), 0x00, (byte) (0x05 + cbytes.length), 0x4F, 0x4B, 0x25, 0x25, 0x25}, cbytes));
			data = sender.getMessage(socket);
			counter++;
		}
		log("SETS end");
		if (data.substring(12, data.length() - 3).replaceAll("\\s+", "_").equals("at$boot_end")) {
			lastSend = sender.sendMessage(socket, createMessage(new byte[] {(modeSet ? (byte) 0x05 : (byte) 0x04), 0x00, 0x04, 0x4F, 0x4B, 0x0D, 0x0A}));
		} else {
			log("MODEM: COMMAND '" + data + "' WAS REJECTED");
		}
	}
	
	protected <T> byte[] toBytes(T counter) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (char c : counter.toString().toCharArray()) {
			baos.write((byte) c);
		}
		return baos.toByteArray();
	}
	
	protected String helloMessage(final boolean modeEncapsulation) {
		return "AT$IMEI=" + imei + ",PSW=" + settings.getPsw() + ",TYP=" + settings.getTyp() + ",DEV=" + settings.getDev() + 
				",VER=" + settings.getVer() + ",REV=" + settings.getRev() + ",BLD=" + settings.getBld() + ",HDW=" + settings.getHdw() + 
				",SIM=" + settings.getSim() + ",CSQ=" + settings.getStringCsq() + (modeEncapsulation ? ",COLPROT=1.3" : "") +
				",ATP=" + settings.getAtp() + ",INT=" + settings.getInp() + (settings.getChannel() > 0 ? ",CHN=" + settings.getChannel() : "") +
				(settings.isModeServer() ? ",MOD=SRV,IP=" + settings.getServerAddress().split(":", 2)[0] + 
					",PORT=" + settings.getServerAddress().split(":", 2)[1] : "") + ",\n";
	}
	
	protected byte[] createMessage(byte [] ...args) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			baos.write(prefix);
			for (byte[] arg : args) {
				if (arg != null) {
					baos.write(arg);
				}
			}
			baos.write(suffix);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return baos.toByteArray();
	}
	
	protected void log(final String message) {
		logger.log(imei, message);
	}
	
}
