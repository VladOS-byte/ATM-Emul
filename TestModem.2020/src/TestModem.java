
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Pattern;

public class TestModem {

	private static final Set<Character> modes = Set.of(
		'F', //M + S
		'M', //Modem
		'S', //Server
		'R'  //Router
	);
	
	public static void main(String[] args) throws IOException {
		
		//String.valueOf(mode), String.valueOf(count), String.valueOf(dif), String.valueOf(timeout)
		char mode = 'F';
		int countModems = 500;
		long differenceImei = 0;
		int timeBetween = 150;
		String set = "Settings.txt";
		
		try {
			for (int i = 0; i < args.length; i++) {
				switch (args[i++].toLowerCase()) {
				case "-m": {
					mode = args[i].charAt(0);
					continue;
				}
				case "-c": {
					countModems = Integer.parseInt(args[i]);
					if (countModems <= 0) {
						throw new NumberFormatException();
					}
					continue;
				}
				case "-d": {
					differenceImei = Long.parseLong(args[i]);
					if (differenceImei <= 0) {
						throw new NumberFormatException();
					}
					continue;
				}
				case "-t": {
					timeBetween = Integer.parseInt(args[i]);
					if (timeBetween <= 0) {
						throw new NumberFormatException();
					}
					continue;
				}
				case "-s": {
					set = args[i];
					continue;
				}
				case "-h" :
				case "-help": {
					System.out.println("Usage: java TestModem [-option argument]\n"
							+ "Options:\n"
							+ "	-m <letter in upper case - mode of working program>\n"
							+ "	-c <integer - count of virtual devices>\n"
							+ "	-d <long integer - difference between IMEI of start device and 1_000_000_000_000_000>\n"
							+ "	-t <integer - timeout between creating of devices\n"
							+ "	-s <string - name of settings file in 'set' folder>\n");
					return;
				}
				default: {
					System.out.println("UNKNOWN OPTION '" + args[i - 1] + "'");
					return;
				}
				}
			}
		} catch (NumberFormatException e) {
			System.out.println("INCORRECT COMMAND: WRONG FORMAT OF NUMBER");
			return;
		} catch (IndexOutOfBoundsException e) {
			System.out.println("INCORRECT COMMAND: EXPECTED ARGUMENT");
			return;
		}
		
		if (!modes.contains(mode)) {
			System.out.println("UNKNOWN MODE '" + mode + "'");
			return;
		}
		
		System.out.println("HELLO!\n"
				+ "VIRTUAL MODEM TEST PROGRAM\n"
				+ "AUTHOR @VLAD PAVLOV (TG @singsoflife)\n"
				+ "YOU CAN CUSTOMIZE SETTINGS OF TESTING IN FILE 'set/Settings.txt'\n"
				+ "LOGS IN FILE 'logs/VM.txt'\n"
				+ "CTRL+C INTERRUPT PROGRAM\n\n"
				+ "PRESS ENTER TO START TESTING\n");
		
		Map<String, Pair<String, Settings>> items = new LinkedHashMap<>();
		Map<String, Pair<Modem, Server>> modems = new HashMap<>();
		
		try (Scanner sc = new Scanner(System.in)) {
			sc.nextLine();
			Logger logger = new Logger("logs/VM.txt", false);
			
			try {
				
				for (long i = differenceImei; i < countModems + differenceImei; i++) {
					Settings settings = new Settings(set);
					String imei = String.valueOf(1000_000_000_000_000L + i);
					items.put(mode == 'R' ? "TEST" + imei.substring(10) : imei,
							new Pair<String, Settings>(String.valueOf((settings.getPort() + i) % 65536), settings));
				}
				
				int counter = 0;

				for (Map.Entry<String, Pair<String, Settings>> item : items.entrySet()) {
					if (++counter % 100 == 0) {
						System.out.println("Done: " + counter);
					}
					Thread.sleep(timeBetween);
					
					String imei = item.getKey();
					Modem m = null;
					Server s = null;

					if (mode == 'R') {
						m = new Router(imei, Integer.parseInt(item.getValue().getKey()), item.getValue().getValue(), logger);
					} else if (mode != 'S') {
						m = new Modem(imei, Integer.parseInt(item.getValue().getKey()), item.getValue().getValue(), logger);
					}

					if (mode != 'M' && mode != 'R') {
						s = new Server(imei, Integer.parseInt(item.getValue().getKey()), item.getValue().getValue(), logger);
					}
					modems.put(imei, new Pair<>(m, s));
				}

				System.out.println("Done");
				System.out.println("");
				if (mode == 'R') {
					System.out.println("OPTIONS:\nexit\nsend something (JSON): TEST[0-9]{6} SEND (.+)");
				} else if (mode != 'S') {
					System.out.println("OPTIONS:\nexit\ndata turn on/off: <imei> MODEDATA ON/OFF\ngpio turn input, output: <imei> GPIO <x> SET <y: [0 - I(0), 1 - I(1), 2 - O(0), 3 - O(1)]>");
				} else {
					System.out.println("OPTIONS:\nexit\ndata turn on/off: <imei> MODEDATA ON/OFF>");
				}
				
				while (true) {
					String cmd = sc.nextLine();
					String command = cmd.toUpperCase();
					if ((mode != 'R') && Pattern.matches("[0-9]{16} MODEDATA (ON|OFF)", command)) {
						Pair<String, Settings> item = items.get(command.substring(0, 16));
						if (item != null) {
							item.getValue().setModeData(command.charAt(27) == 'N');
							System.out.println("MODEDATA TURN " + (command.charAt(27) == 'N' ? "IN" : "OUT"));
						}
					} else if ((mode == 'M' || mode == 'F') && Pattern.matches("[0-9]{16} GPIO [1-4] SET [0-3]", command)) {
						String imei = command.substring(0, 16);
						Pair<String, Settings> item = items.get(imei);
						Pair<Modem, Server> modemPair = modems.get(imei);
						if (item != null && modemPair != null && modemPair.getKey() != null) {
							item.getValue().setGpioX(command.charAt(22) - 48, command.charAt(28) - 48);
							modemPair.getKey().sendGpio();
							System.out.println("GPIO " + command.charAt(22) + " TURN " + (command.charAt(28) != '2' ? "IN" + command.charAt(28) : "OUT"));
						}
					} else if (mode == 'R' && Pattern.matches("TEST[0-9]{6} SEND (.+)", command)) {
						String imei = cmd.substring(0, 10);
						Pair<String, Settings> item = items.get(imei);
						Pair<Modem, Server> modemPair = modems.get(imei);
						if (item != null && modemPair != null && modemPair.getKey() != null) {
							modemPair.getKey().sendCommand(cmd.substring(15).trim());
							System.out.println("Send: " + cmd.substring(15).trim());
						}
					} else if (command.equalsIgnoreCase("exit")) {
						sc.close();
						System.out.println("Bye");
						return;
					} else {
						System.out.println("UnsupportedOperationException [command: " + command + "]");
					}
					if (mode == 'R') {
						System.out.println("OPTIONS:\nexit\nsend something (JSON): TEST[0-9]{6} SEND (.+)");
					} else if (mode != 'S') {
						System.out.println("OPTIONS:\nexit\ndata turn on/off: <imei> MODEDATA ON/OFF\ngpio turn input, output: <imei> GPIO <x> SET <y: [0 - I(0), 1 - I(1), 2 - O(0), 3 - O(1)]>");
					} else {
						System.out.println("OPTIONS:\nexit\ndata turn on/off: <imei> MODEDATA ON/OFF>");
					}
				}
			} catch (InterruptedException | NoSuchElementException e) {
				System.out.println("PROGRAM INTERRUPTED");
				e.printStackTrace();
			} finally {
				logger.close();
			}
			
		} catch (UnsupportedEncodingException e) {
			System.out.println("Settings Exception");
		} catch (IOException e) {
			System.out.println("Logger Exception");
		}
	}
	
}
