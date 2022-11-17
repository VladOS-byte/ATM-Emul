import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

	private PrintWriter writer;
	
	private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private String cache = "";
	
	public Logger(final String fileName, boolean append) throws IOException {
		this.writer = new PrintWriter(new FileOutputStream(fileName, append));
	}

	public void log(final String imei, final String message) {
		writer.println("[" + format.format(new Date()) + "]: " + imei + " \\ " + message + (cache.equals(imei) ? "" : "\n"));
		writer.flush();
		cache = imei;
	}
	
	public void close() {
		writer.close();
	}
}
