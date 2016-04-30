package downloader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Downloader {
	
	static String baseURL = "http://gd2.mlb.com/components/game/mlb";
	static String baseLocalURL;
	static ArrayList<String> files = new ArrayList<String>();
	static int maxThreads = 8;
	Semaphore s;
	
	private static void print(String s) {
		System.out.println(s);
	}
	
	public static void main(String[] args) {
		Downloader downloader = new Downloader();
		int year;
		if (args.length == 0) {
			year = Calendar.getInstance().get(Calendar.YEAR); 
			baseLocalURL = getOSPath(System.getProperty("user.dir")+"\\out");
			print("No year specified, using " + year);
			print("No directory specified, output can be found at:\n" + baseLocalURL);
		}
		else if (args.length == 1) {
			year = Integer.parseInt(args[0]);
			baseLocalURL = getOSPath(System.getProperty("user.dir")+"\\out");
			print("No directory specified, output can be found at:\n" + baseLocalURL);
		}
		else {
			year = Integer.parseInt(args[0]);
			baseLocalURL = args[1];
			print("Output can be found at:\n" + baseLocalURL);
		}
		if (args.length > 2) {
			for (int i = 2; i < args.length; i++) {
				files.add(args[i]);
			}
		}
		else {
			print("No filenames specified, using default: game_events.json, boxscore.json, inning_all.xml, inning_hit.xml, inning_Scores.xml");
			files.add("game_events.json");
			files.add("boxscore.json");
			files.add("inning_all.xml");
			files.add("inning_hit.xml");
			files.add("inning_Scores.xml");
		}
		if (args.length > 0) {
			String lastArg = args[args.length-1];
			if (StringUtil.isNumeric(lastArg)) {
				maxThreads = Integer.parseInt(lastArg);
			}	
		}
		downloader.download(year);
	}
	
	public void download(int year) {
		print("Starting with "+maxThreads+" threads");
		s = new Semaphore(80);
		for(int month = 4 ; month <= 11 ; month++) {
			download(year, month);
		}
	}

	public void download(int year, int month) {
		try {
			ExecutorService executor = Executors.newCachedThreadPool();
			for( int day = 1 ; day <= 31 ; day++) {
				String parentFolder = String.format("%s/year_%02d/month_%02d/day_%02d/",
						baseURL,
						year,
						month,
						day);

				ArrayList<String> gameFolders = new ArrayList<String>();
				try {
					Document doc = Jsoup.connect(parentFolder).post();
					Document parse = Jsoup.parse(doc.html());
					Elements lis = parse.select("li > a");
					for (Element li : lis) {
						String href = li.attr("href");
						if (href.startsWith("gid_")) {
							gameFolders.add(href.replace("/", ""));
						}
					}
				} catch(IOException e) {
					return;
				}
				
				
				for (String gameFolder : gameFolders) {
					String directory = String.format("%s/year_%02d/month_%02d/day_%02d/%s",
							baseURL,
							year,
							month,
							day,
							gameFolder);
					
					for (String filename : files) {
						URL u;
						if (filename.contains("inning_")) {
							
							u = new URL(String.format("%s/%s/%s", directory, "inning", filename));
						}
						else {
							u = new URL(String.format("%s/%s", directory, filename));
						}

						File file = new File(getOSPath(String.format("%s\\%s",baseLocalURL, u.getFile())));
						if (file.exists()) {
							continue;
						}
						s.acquire();
						executor.submit(new FileDownloader(u, file));
					}
				}
			}
			executor.shutdown();
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	class FileDownloader implements Callable<String> {
		URL url;
		File file;
		FileDownloader(URL u, File f) {
			url = u;
			file = f;
		}

		@Override
		public String call() {
			try {
				FileUtils.copyURLToFile(url, file, 5000, 5000);
			} catch (IOException e) {
				if (file.getName().equalsIgnoreCase("game_events.json") || file.getName().equalsIgnoreCase("boxscore.json")) {
					URL u = null;
					try {
						u = new URL("http://"+url.getHost()+url.getPath().replace("json", "xml"));
						File f = new File(getOSPath(String.format("%s\\%s",baseLocalURL, u.getFile())));
						FileUtils.copyURLToFile(u, f, 5000, 5000);
					} catch (IOException e1) {
						s.release();
						return null;
					}
				}
				else if (file.getName().equalsIgnoreCase("inning_all.xml")) {
					try {
						int i = 1;
						boolean active = true;
						while (active) {
							String sURL = "http://"+url.getHost()+url.getPath().replace("inning_all.xml", "inning_"+i+".xml");
							Connection c = Jsoup.connect(sURL).header("Method", "HEAD");
							if (c.execute().statusCode() == 200) {
								i++;
								URL u = new URL(sURL);
								File f = new File(getOSPath(baseLocalURL + "\\" + u.getFile()));
								FileUtils.copyURLToFile(u, f, 5000, 5000);
							}
							else {
								active = false;
							}
						}
					} catch (IOException e1) {
						//We expect this exception to throw since we just count up from 1, and the amount of innings
						//in a game is usually 9 so it will throw when it runs out of innings. This is just to make
						//it easier by not having to figure out how many innings were actually in the game
						s.release();
						return null;
					}
					
				}
				else {
				}
				s.release();
				return null;
			}
			s.release();
			return file.getAbsolutePath();
		}
	}
	
	private static String getOSPath(String path) {
		if (SystemUtils.IS_OS_MAC) return path.replaceAll("\\", "/");
		else return path;
	}
}
