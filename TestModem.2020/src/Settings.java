import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Settings {
	
	private final String psw = "5492";						//Password
	private final String typ = "ATM";						//Type of device
	private final String dev = "ATM31";						//Device
	private final String ver = "01";						//Version of firmware
	private final String rev = "03";						//Version of revision
	private final String bld = "016.353";					//Version build
	private final String hdw = "2.0";						//HW
	private final String sim = "0";							//Slot of SIM
	private int csq = 0;									//CSQ (1-31); 0 - random
	private final String atp = "1.1";						//ATP?
	private final String inp = "485+232";					//Input
	private String hostMainServer = "192.168.245.96";		//Host IRZ-Server
	private int portMainServer = 24841;						//Port IRZ-Server
	private final boolean modeEncapsulation = true;			//Mode of encapsulation
	private final boolean modeServer = false;				//Mode of server
	private final String serverAddress = "127.0.0.1:5000";	//Mode of encapsulation
	private int dataSize = 1024;							//Size of data package [1; 1180]
	private final int minTO = 1;							//minimum timeout of server's sending
	private final int maxTO = 2;							//maximum timeout of server's sending
	private boolean modeData = false;						//Mode of data sending
	private char modeBadCSQ = 'F';							//F - off; T - on; R - randomly by CSQ;
	private int tmp = 0;									//temperature of module [-100; 125] / else randomly
	private final boolean modeCheckSet = false;				//Mode of SET
	private final boolean modeCheckFrm = false;				//Mode of FRM
	private final int port = 35000;							//Slot of SIM
	private String gpio = "0000";							//gpio
	private int keepTime = 50;								//keep alive time
	private int channel = 0;								//Channel
	private int gpioChangeTimeout = -1;						//time out for gpio change (<0: off; 0: random time; >5: on)
	private int expectedGpioTimeout = 120;					//Expected GPIO change timeout [120; 2^31)

	private final Random random = new Random();
	
	private Map<Byte, Pair<byte[], byte[]>> answers = new LinkedHashMap<>();
	
	private ArrayList<String> modes;
	
	public Settings(final String fileName) throws FileNotFoundException, UnsupportedEncodingException {
		init("set/" + fileName);
	}
	
	private void init(final String fileName) throws FileNotFoundException, UnsupportedEncodingException {
		
		modes = new ArrayList<>();
		try (Scanner reader = new Scanner(new FileInputStream(fileName))) {
			String mode;
			int num = 0;
			while (reader.hasNextLine()) {
				mode = reader.nextLine();
				num++;
				if (!mode.isEmpty()) {
					if (Pattern.matches("<[\\w|.|:|+|-]+>(.*)", mode)) {
						modes.add(mode.substring(1, mode.indexOf('>')));
					} else {
						System.out.println("WARNING! IN " + num + " LINE OF SETTINGS FOUND BAD EXPRESSION. "
								+ "PRESS ENTER TO CONTINUE (THIS MODE WOULD SKIPPED)");
						new Scanner(System.in).nextLine();
						modes.add("_SKIPPED");
					}
				}
			}
		} catch (NullPointerException ignored) {
			
		} catch (FileNotFoundException e) {
			System.out.println("ERROR! SETTINGS FILE NOT FOUND\nSETTINGS WOULD USED BY DEFAULT");
		}
		
		answers.put((Byte)(byte)0x01, new Pair<>(new byte[] {(byte)0x0C,(byte) 0x00, (byte)0x04, (byte)0x01, (byte)0x40, (byte)0x01, (byte) ((char) getCsq())}, null));
	
		answers.put((Byte)(byte)0x02, new Pair<>(
				new byte[] {(byte)0x0C, (byte)0x00, (byte)0x07, (byte)0x01, (byte)0x40, (byte)0x02, getGPIO(1), getGPIO(2), getGPIO(3), getGPIO(4)}, null));
		
		answers.put((Byte)(byte)0x03, new Pair<>(new byte[] {(byte)0x0C, (byte)0x00, (byte)0x04,(byte) 0x01, (byte)0x40, (byte)0x03, (byte)0x3F}, null));
		
		answers.put((Byte)(byte)0x04, new Pair<>(new byte[] {(byte)0x0C, (byte)0x00, (byte) 0xAC, (byte)0x01, (byte)0x40, (byte)0x04}, 
				("0,\"250,01,00dd,3860,29,21\";1,\"250,01,00dd,3863,27,17\";2,\"250,01,00dd,25bd,44,04\";3,\",,0000,0000,00,00\";"
				+ "4,\",,0000,0000,00,00\";5,\",,0000,0000,00,00\";6,\",,0000,0000,00,00\";").getBytes("US-ASCII")));
		
		answers.put((Byte)(byte)0x05, new Pair<>(
				new byte[] {(byte)0x0C, (byte)0x00, (byte)0x04, (byte)0x01, (byte)0x40, (byte)0x05, (byte) ((char)(getTmp() + 100))}, null));
		
		answers.put((Byte)(byte)0x0B, new Pair<>(
				new byte[] {(byte)0x0C, (byte)0x00, (byte)0x16,(byte) 0x01, (byte)0x40, (byte)0x0B}, "897019932724195983F".getBytes("US-ASCII")));
	}
	
	private String getOrDefault(final int index, final String defaultAnswer) {
		if (modes.size() <= index) {
			return defaultAnswer;
		}
		String answer = modes.get(index);
		return (answer.equals("_SKIPPED") ? defaultAnswer : answer);
	}
	
	public Pair<byte[], byte[]> getAnswer(final byte command) {
		return answers.get(command);
	}
	
	public String getPsw() {
		return getOrDefault(0, psw);
	}
	
	public String getTyp() {
		return getOrDefault(1, typ);
	}

	public String getDev() {
		return getOrDefault(2, dev);
	}

	public String getVer() {
		return getOrDefault(3, ver);
	}

	public String getRev() {
		return getOrDefault(4, rev);
	}

	public String getBld() {
		return getOrDefault(5, bld);
	}

	public String getHdw() {
		return getOrDefault(6, hdw);
	}

	public String getSim() {
		return getOrDefault(7, sim);
	}

	public String getStringCsq() {
		return String.valueOf(getCsq());
	}
	
	public int getCsq() {
		int tmp = Integer.parseInt(getOrDefault(8, String.valueOf(csq)));
		return tmp > 0 && tmp <= 31 ? tmp : random.nextInt(30) + 1;
	}

	public String getAtp() {
		return getOrDefault(9, atp);
	}

	public String getInp() {
		return getOrDefault(10, inp);
	}

	public String getHostMainServer() {
		return getOrDefault(11, hostMainServer);
	}

	public int getPortMainServer() {
		return Integer.parseInt(getOrDefault(12, String.valueOf(portMainServer)));
	}

	public boolean isModeEncapsulation() {
		return Boolean.valueOf(getOrDefault(13, String.valueOf(modeEncapsulation)));
	}

	public boolean isModeServer() {
		return Boolean.valueOf(getOrDefault(14, String.valueOf(modeServer)));
	}

	public String getServerAddress() {
		return getOrDefault(15, serverAddress);
	}

	public int getDataSize() {
		int tmp = Integer.parseInt(getOrDefault(16, String.valueOf(dataSize)));
		if (tmp > 1448) {
			tmp = 1448;
		} else if (tmp < 2) {
			tmp = 2;
		}
		return tmp;
	}

	public int getMinTO() {
		return Integer.parseInt(getOrDefault(17, String.valueOf(minTO)));
	}

	public int getMaxTO() {
		return Integer.parseInt(getOrDefault(18, String.valueOf(maxTO)));
	}

	public boolean isModeData() {
		modeData = Boolean.valueOf(getOrDefault(19, String.valueOf(modeData)));
		return modeData;
	}

	public void setModeData(final boolean modeData) {
		this.modeData = modeData;
		if (modes.size() > 19) {
			modes.set(19, "_SKIPPED");
		}
	}

	public boolean isModeBadCSQ() {
		char tmp = getOrDefault(20, String.valueOf(modeBadCSQ)).charAt(0);
		return tmp == 'T' || (tmp == 'R' && getCsq() < 5);
	}
	
	public int getTmp() {
		int tmp = Integer.parseInt(getOrDefault(21, String.valueOf(this.tmp)));
		return tmp >= -100 && tmp <= 155 ? tmp : (random.nextInt(255) - 100);
	}

	public boolean isModeCheckSet() {
		return Boolean.valueOf(getOrDefault(22, String.valueOf(modeCheckSet)));
	}

	public boolean isModeCheckFrm() {
		return Boolean.valueOf(getOrDefault(23, String.valueOf(modeCheckFrm)));
	}
	
	public int getPort() {
		return Integer.parseInt(getOrDefault(24, String.valueOf(port)));
	}

	public byte getGPIO(int x) {
		String tmp = getOrDefault(25, gpio);
		x--;
		int gp = ((tmp.length() > x ? getOrDefault(25, gpio) : gpio).charAt(x) - 48);
		return (byte) ((gp < 2 ? 0x80 : 0xC0) + (gp % 2) * 2);
	}
	
	public void setGpioX(final int x, final int value) {
		gpio = getOrDefault(25, gpio);
		System.out.println(gpio);
		if (modes.size() > 25) {
			modes.set(25, "_SKIPPED");
		}
		gpio = (x > 1 ? gpio.substring(0, x - 1) : "") + value + (x < gpio.length() ? gpio.substring(x) : "");
		System.out.println(gpio);
		answers.put((Byte)(byte)0x02, new Pair<>(
				new byte[] {(byte)0x0C, (byte)0x00, (byte)0x07, (byte)0x01, (byte)0x40, (byte)0x02, getGPIO(1), getGPIO(2), getGPIO(3), getGPIO(4)}, null));
	}

	public int getKeepTime() {
		return Integer.parseInt(getOrDefault(26, String.valueOf(keepTime))) * 1000;
	}

	public int getChannel() {
		return Integer.parseInt(getOrDefault(27, String.valueOf(channel)));
	}

	public int getGpioChangeTimeout() {
		int y = Integer.parseInt(getOrDefault(28, String.valueOf(gpioChangeTimeout)));

		if (y == 0) {
			y = random.nextInt(Math.max(expectedGpioTimeout, Integer.parseInt(getOrDefault(29, String.valueOf(expectedGpioTimeout)))));
		}

		return y;
	}
}
