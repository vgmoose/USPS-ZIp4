import java.io.File;
import java.io.IOException;
import java.awt.datatransfer.*;
import java.awt.Toolkit;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.text.html.FormSubmitEvent;

/**
 * Get a web file.
 */
public final class ZipFetcher extends JFrame {
	// Saved response.
	private java.util.Map<String,java.util.List<String>> responseHeader = null;
	private java.net.URL responseURL = null;
	private int responseCode = -1;
	private String MIMEtype  = null;
	private String charset   = null;
	private Object content   = null;
	private JEditorPane htmlPanel;
	private Entry[] entries;
	int curIndex = 0;

	public static class URLParamEncoder {

		public static String encode(String input) {
			StringBuilder resultStr = new StringBuilder();
			for (char ch : input.toCharArray()) {
				if (isUnsafe(ch)) {
					resultStr.append('%');
					resultStr.append(toHex(ch / 16));
					resultStr.append(toHex(ch % 16));
				} else {
					resultStr.append(ch);
				}
			}
			return resultStr.toString();
		}

		private static char toHex(int ch) {
			return (char) (ch < 10 ? '0' + ch : 'A' + ch - 10);
		}

		private static boolean isUnsafe(char ch) {
			if (ch > 128 || ch < 0)
				return true;
			return " %$&+,/:;=?@<>#%".indexOf(ch) >= 0;
		}

	}

	public void copyToClipboard(String copy)
	{
		StringSelection stringSelection = new StringSelection (copy);
		Clipboard clpbrd = Toolkit.getDefaultToolkit ().getSystemClipboard ();
		clpbrd.setContents (stringSelection, null);
	}
	public static class Entry
	{
		private String zip;
		private String address;
		private String no;
		private String name;

		public static Entry[] parseEntries(String data)
		{
			ArrayList<Entry> entries = new ArrayList<Entry>();

			StringTokenizer st = new StringTokenizer(data, "\t");
			int count = 0;
			Entry curEntry = new Entry();
			while (st.hasMoreTokens()) {
				count++;
				if (count == 1)
					curEntry.name = st.nextToken();
				else if (count == 2)
					curEntry.no = st.nextToken();
				else if (count == 3)
					curEntry.address = URLParamEncoder.encode(st.nextToken());
				else if (count == 4)
				{
					curEntry.zip = st.nextToken();
					String overflow = "";
					if (curEntry.zip.contains(" "))
					{
						int pos = curEntry.zip.indexOf(" ");
						overflow = curEntry.zip.substring(pos+1);
						curEntry.zip = curEntry.zip.substring(0, pos);
					}

					if (curEntry.zip.length() < 5)
						curEntry.zip = "00000".substring(curEntry.zip.length()) + curEntry.zip;
					entries.add(curEntry);
					curEntry = new Entry();

					if (!overflow.isEmpty())
					{
						count = 1;
						curEntry.name = overflow;
					}
				}
				count%= 4;
			}
			Entry[] entryArray = new Entry[entries.size()];
			entryArray = entries.toArray(entryArray);

			return entryArray;
		}
	}

	public static void main(String[] args) throws MalformedURLException, IOException
	{
		String data = JOptionPane.showInputDialog("Copy and paste multiple rows of zip codes in here.\nCustomer, Number, Address, Zip");
		Entry[] entries = Entry.parseEntries(data);
		ZipFetcher window = new ZipFetcher(entries);
	}

	public String makeHTML(Entry[] entries, int index) throws MalformedURLException, IOException
	{
		String companyName = "";
		String address1 = entries[index].address;
		String address2 = "";
		String city = "";
		String state = "";
		String zip = entries[index].zip;

		fetch("https://tools.usps.com/go/ZipLookupResultsAction!input.action?resultMode=1&companyName="+companyName+"&address1="+address1+"&address2="+address2+"&city="+city+"+&state="+state+"&urbanCode=&postalCode=&zip="+zip+"");
		String response = (String) getContent();

		URL h =  getClass().getClassLoader().getResource("usps.png");
		System.out.println(h);

		String allHTML = "<img src='"+h+"'></img><table width='580'><tr><td>";
		if (index > 0)
			allHTML += "<form action=\"#\"><input type=\"hidden\" name=\"prev\" value="+(index-1)+"></input><input type=submit value='Prev'></input></form>";
		allHTML += "</td><td>"+entries[index].name+" " +entries[index].no+"</td><td>";
		if (index < entries.length-1)
			allHTML += "<form action=\"#\"><input type=\"hidden\" name=\"next\" value="+(index+1)+"></input><input type=submit value='Next'></input></form>";
		allHTML += "</td></tr></table><div class=content><table>";

		int count = 0;
		boolean firstZip = true;
		while (true)
		{
			int lpos = response.indexOf("<p class=\"std-address\">");
			if (lpos == -1)
				break;
			int rpos = response.substring(lpos).indexOf("</p>");
			String thisChunk = response.substring(lpos+55, lpos+rpos+4-80-62);
			if (thisChunk.contains("zip4"))
			{
				int zposl = thisChunk.indexOf("zip4")+6;
				int zposr = thisChunk.substring(zposl).indexOf("</");

				if (count%2 == 0)
					allHTML += "<tr>";

				String targetZip = thisChunk.substring(zposl, zposl+zposr);
				
				if (firstZip)
				{
					copyToClipboard(targetZip);
					firstZip = false;
				}
				System.out.println(targetZip);
				
				allHTML += "<td><div class=\"zippy\">"+ thisChunk + "<form action=\"#\"><input type=\"hidden\" name=\"zip\" value=\""+targetZip+"\"></input><input type=submit value='Copy'></input></form></div></td>";

				if (count%2 == 1)
					allHTML += "</tr>";
				count++;

			}
			response = response.substring(rpos+4+lpos);
		}

		allHTML += "</table>";

		return allHTML;
	}

	void start()
	{
		try { 
			HTMLEditorKit kit = new HTMLEditorKit();
			JEditorPane ed1=new JEditorPane("text/html", "Loading...");

			kit.setAutoFormSubmission(false);
			ed1.addHyperlinkListener(new HyperlinkListener()
			{
				@Override
				public void hyperlinkUpdate(HyperlinkEvent e)
				{
					if (e instanceof FormSubmitEvent)
					{
						try {
							if (((FormSubmitEvent)e).getData().startsWith("next"))
								htmlPanel.setText(makeHTML(entries, ++curIndex));
							if (((FormSubmitEvent)e).getData().startsWith("prev"))
								htmlPanel.setText(makeHTML(entries, --curIndex));
						} catch (MalformedURLException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}

						if (((FormSubmitEvent)e).getData().startsWith("zip"))
						{
							String zip = ((FormSubmitEvent)e).getData().substring(4);
							copyToClipboard(zip);
						}
					}
				}
			});
			StyleSheet styles = kit.getStyleSheet();
			//    		 styles.addRule("a:link { color: white; }");
			styles.addRule(".zippy { border: 1px dotted black; width: 180px; text-align: center; padding: 10px; font-family: Arial, sans-serif;float: left;display: inline;}");
			styles.addRule(".zip4 { font-weight: bold; }");
			styles.addRule("form { display: inline-block; }");
			styles.addRule(".content { padding: 10px; }");
			styles.addRule("table { padding-left: 20px; padding-right: 20px; }");


			ed1.setEditorKit(kit);

			htmlPanel = ed1;

			Document doc = kit.createDefaultDocument();
			ed1.setDocument(doc);

			this.add(ed1); setVisible(true);
			JScrollPane scrPane = new JScrollPane(ed1);
			this.add(scrPane);

			setSize(600,600);
			setDefaultCloseOperation(EXIT_ON_CLOSE);
		}
		catch(Exception e) { e.printStackTrace(); System.out.println("Some problem has occured"+e.getMessage()); }
	}

	/** Open a web file. */
	public void fetch( String urlString )
			throws java.net.MalformedURLException, java.io.IOException {
		// Open a URL connection.
		final java.net.URL url = new java.net.URL( urlString );
		final java.net.URLConnection uconn = url.openConnection( );
		if ( !(uconn instanceof java.net.HttpURLConnection) )
			throw new java.lang.IllegalArgumentException(
					"URL protocol must be HTTP." );
		final java.net.HttpURLConnection conn =
				(java.net.HttpURLConnection)uconn;

		// Set up a request.
		conn.setConnectTimeout( 10000 );    // 10 sec
		conn.setReadTimeout( 10000 );       // 10 sec
		conn.setInstanceFollowRedirects( true );
		conn.setRequestProperty( "User-agent", "spider" );

		// Send the request.
		conn.connect( );

		// Get the response.
		responseHeader    = conn.getHeaderFields( );
		responseCode      = conn.getResponseCode( );
		responseURL       = conn.getURL( );
		final int length  = conn.getContentLength( );
		final String type = conn.getContentType( );
		if ( type != null ) {
			final String[] parts = type.split( ";" );
			MIMEtype = parts[0].trim( );
			for ( int i = 1; i < parts.length && charset == null; i++ ) {
				final String t  = parts[i].trim( );
				final int index = t.toLowerCase( ).indexOf( "charset=" );
				if ( index != -1 )
					charset = t.substring( index+8 );
			}
		}

		// Get the content.
		final java.io.InputStream stream = conn.getErrorStream( );
		if ( stream != null )
			content = readStream( length, stream );
		else if ( (content = conn.getContent( )) != null &&
				content instanceof java.io.InputStream )
			content = readStream( length, (java.io.InputStream)content );
		conn.disconnect( );
	}

	public ZipFetcher(Entry[] entries) throws MalformedURLException, IOException {
		start();
		this.entries = entries;
		this.htmlPanel.setText(makeHTML(this.entries, 0));
	}

	/** Read stream bytes and transcode. */
	private Object readStream( int length, java.io.InputStream stream )
			throws java.io.IOException {
		final int buflen = Math.max( 1024, Math.max( length, stream.available() ) );
		byte[] buf   = new byte[buflen];;
		byte[] bytes = null;

		for ( int nRead = stream.read(buf); nRead != -1; nRead = stream.read(buf) ) {
			if ( bytes == null ) {
				bytes = buf;
				buf   = new byte[buflen];
				continue;
			}
			final byte[] newBytes = new byte[ bytes.length + nRead ];
			System.arraycopy( bytes, 0, newBytes, 0, bytes.length );
			System.arraycopy( buf, 0, newBytes, bytes.length, nRead );
			bytes = newBytes;
		}

		if ( charset == null )
			return bytes;
		try {
			return new String( bytes, charset );
		}
		catch ( java.io.UnsupportedEncodingException e ) { }
		return bytes;
	}

	/** Get the content. */
	public Object getContent( ) {
		return content;
	}

	/** Get the response code. */
	public int getResponseCode( ) {
		return responseCode;
	}

	/** Get the response header. */
	public java.util.Map<String,java.util.List<String>> getHeaderFields( ) {
		return responseHeader;
	}

	/** Get the URL of the received page. */
	public java.net.URL getURL( ) {
		return responseURL;
	}

	/** Get the MIME type. */
	public String getMIMEType( ) {
		return MIMEtype;
	}
}