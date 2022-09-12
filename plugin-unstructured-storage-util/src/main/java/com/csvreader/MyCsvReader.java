package com.csvreader;

import java.io.*;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.HashMap;

/**
 * {@code @Classname} MyCsvReader
 * {@code @Description} TODO
 * {@code @Date} 2022/9/10 23:16
 * {@code @Created} by xiaolin
 */

public class MyCsvReader {

    private Reader inputStream;//输入流
    private String fileName;//文件名
    private MyCsvReader.UserSettings userSettings;//解析配置文件生成的配置类
    private Charset charset;//字符集
    private boolean useCustomRecordDelimiter;//是否使用自定义记录分隔符
    private MyCsvReader.DataBuffer dataBuffer;
    private MyCsvReader.ColumnBuffer columnBuffer;
    private MyCsvReader.RawRecordBuffer rawBuffer;
    private boolean[] isQualified;
    private String rawRecord;
    private MyCsvReader.HeadersHolder headersHolder;
    private boolean startedColumn; //当前是否开始了字段读取
    private boolean startedWithQualifier;//是否已文本限定符开头
    private boolean hasMoreData;//输入流是否有数据
    private String lastLetter;
    private boolean hasReadNextLine;//是否继续下一行的读取
    private int columnsCount;
    private long currentRecord;
    private String[] values;//存放已解析的行字段值数组
    private boolean initialized;
    private boolean closed;//是否关闭读取

    private class RawRecordBuffer {
        public char[] Buffer = new char[500];
        public int Position = 0;

        public RawRecordBuffer() {
        }
    }

    private class ColumnBuffer {
        public char[] Buffer = new char[50];
        public int Position = 0;

        public ColumnBuffer() {
        }
    }

    // 输入流的数据缓冲区，一次读取1024个字节
    private class DataBuffer {
        public char[] Buffer = new char[1024];
        public int Position = 0;//记录当前程序读到的位置
        public int Count = 0;//从输入流中读取的数据长度
        public int ColumnStart = 0;//记录当前解析的字段的起始位置
        public int LineStart = 0;//记录当前行的起始位置

        public DataBuffer() {
        }
    }

    private class UserSettings {
        public char TextQualifier = '"'; //文本限定符
        public boolean TrimWhitespace = true;//是否去空格
        public boolean UseTextQualifier = true; //是否使用文本限定符
        public String Delimiter = ",";//字段分隔符
        public char RecordDelimiter = 0;//记录分隔符
        public char Comment = '#';//注释字符
        public boolean UseComments = false;//是否解析注释
        public boolean SafetySwitch = true;//安全校验开关
        public boolean SkipEmptyRecords = true;//跳过空行
        public boolean CaptureRawRecord = true;//是否捕获行记录

        public UserSettings() {
        }
    }

    /**
     * 从缓冲区中读取记录行，删掉了原CsvReader中没有使用代码
     */
    public boolean readRecord() throws IOException {
        // 定义队列，记录当前读字符往前倒推分隔符长度，判断是否分隔符
        MyCircularFifoQueue myQueue = new MyCircularFifoQueue(this.userSettings.Delimiter.length());
        this.checkClosed();
        this.columnsCount = 0; //当前行已读取的字段数
        this.rawBuffer.Position = 0;//行缓冲区位置
        this.dataBuffer.LineStart = this.dataBuffer.Position;//数据缓冲区中行起始位置
        this.hasReadNextLine = false;//是否有下一行可读，默认不可读，当前行读完才会去校验
        if (this.hasMoreData) {
            while (true) {
                // 初始化，刷新数据缓冲区数据
                if (this.dataBuffer.Position == this.dataBuffer.Count) {
                    this.checkDataLength();
                } else {
                    this.startedWithQualifier = false;//无用
                    // 读取新字段或新行的第一个字符
                    char var1 = this.dataBuffer.Buffer[this.dataBuffer.Position];
                    myQueue.offer(var1);
                    if (this.userSettings.Delimiter.equals(myQueue.toString())) {
                        this.lastLetter = myQueue.toString(); //如果当前读取位置为分隔符
                        this.endColumn();
                    } else if (this.useCustomRecordDelimiter || var1 != '\r' && var1 != '\n') {
                        // 采集数据注释行处理，此判断可以删除
                        if (this.userSettings.UseComments && this.columnsCount == 0 && var1 == this.userSettings.Comment) {
                            this.lastLetter = String.valueOf(var1);
                            this.skipLine();
                            // 字段是否去头空字符串
                        } else if (this.userSettings.TrimWhitespace && (var1 == ' ' || var1 == '\t')) {
                            this.startedColumn = true;
                            this.dataBuffer.ColumnStart = this.dataBuffer.Position + 1;
                            this.lastLetter = String.valueOf(var1);
                        } else {
                            this.startedColumn = true;
                            this.dataBuffer.ColumnStart = this.dataBuffer.Position;
                            boolean var3 = false;
                            byte var4 = 1;
                            int var5 = 0;
                            char var6 = 0;
                            boolean var7 = true;

                            //循环读取字段，由于字段长度不定长，使用while循环，直到读到换行符退出
                            do {
                                if (!var7 && this.dataBuffer.Position == this.dataBuffer.Count) {
                                    this.checkDataLength();//buffer读完，刷新数据缓冲区数据
                                } else {

                                    if (!var7) {
                                        var1 = this.dataBuffer.Buffer[this.dataBuffer.Position];
                                        myQueue.offer(var1);
                                    }

                                    if (var3) {
                                        ++var5;
                                        switch (var4) {
                                            case 1:
                                                var6 = (char) (var6 * 16);
                                                var6 += hexToDec(var1);
                                                if (var5 == 4) {
                                                    var3 = false;
                                                }
                                                break;
                                            case 2:
                                                var6 = (char) (var6 * 8);
                                                var6 += (char) (var1 - 48);
                                                if (var5 == 3) {
                                                    var3 = false;
                                                }
                                                break;
                                            case 3:
                                                var6 = (char) (var6 * 10);
                                                var6 += (char) (var1 - 48);
                                                if (var5 == 3) {
                                                    var3 = false;
                                                }
                                                break;
                                            case 4:
                                                var6 = (char) (var6 * 16);
                                                var6 += hexToDec(var1);
                                                if (var5 == 2) {
                                                    var3 = false;
                                                }
                                        }

                                        if (!var3) {
                                            this.appendLetter(var6);
                                        } else {
                                            this.dataBuffer.ColumnStart = this.dataBuffer.Position + 1;
                                        }
                                    }  else if (this.userSettings.Delimiter.equals(myQueue.toString())) {
                                        //如果var1=分隔符
                                        this.lastLetter = myQueue.toString();
                                        this.endColumn();
                                    } else if (!this.useCustomRecordDelimiter && (var1 == '\r' || var1 == '\n') || this.useCustomRecordDelimiter && var1 == this.userSettings.RecordDelimiter) {
                                        this.lastLetter = String.valueOf(var1);
                                        this.endColumn();
                                        this.endRecord();
                                    }

                                    var7 = false;
                                    if (this.startedColumn) {
                                        ++this.dataBuffer.Position;
                                        if (this.userSettings.SafetySwitch && this.dataBuffer.Position - this.dataBuffer.ColumnStart + this.columnBuffer.Position > 100000) {
                                            this.close();
                                            throw new IOException("Maximum column length of 100,000 exceeded in column " + NumberFormat.getIntegerInstance().format((long) this.columnsCount) + " in record " + NumberFormat.getIntegerInstance().format(this.currentRecord) + ". Set the SafetySwitch property to false" + " if you're expecting column lengths greater than 100,000 characters to" + " avoid this error.");
                                        }
                                    }
                                }
                            } while (this.hasMoreData && this.startedColumn);
                        }
                    } else {
                        this.lastLetter = String.valueOf(var1);
                        if (!this.startedColumn && this.columnsCount <= 0 && (this.userSettings.SkipEmptyRecords || var1 != '\r' && this.lastLetter == String.valueOf('\r'))) {
                            this.dataBuffer.LineStart = this.dataBuffer.Position + 1;
                        } else {
                            this.endColumn();
                            this.endRecord();
                        }
                    }

                    if (this.hasMoreData) {
                        this.dataBuffer.Position++;
                    }
                }

                if (!this.hasMoreData || this.hasReadNextLine) {
                    if (this.startedColumn || this.lastLetter.equals(this.userSettings.Delimiter)) {
                        this.endColumn();
                        this.endRecord();
                    }
                    break;
                }
            }
        }

        // 是否捕获行记录，用于打印
        if (this.userSettings.CaptureRawRecord) {
            if (this.hasMoreData) {
                //如果一行数据读完，buffer还有数据
                if (this.rawBuffer.Position == 0) {
                    this.rawRecord = new String(this.dataBuffer.Buffer, this.dataBuffer.LineStart,
                            this.dataBuffer.Position - this.dataBuffer.LineStart - 1);
                } else {
                    this.rawRecord = new String(this.rawBuffer.Buffer, 0, this.rawBuffer.Position) + new String(this.dataBuffer.Buffer,
                            this.dataBuffer.LineStart, this.dataBuffer.Position - this.dataBuffer.LineStart - 1);
                }
            } else {
                this.rawRecord = new String(this.rawBuffer.Buffer, 0, this.rawBuffer.Position);
            }
        } else {
            this.rawRecord = "";
        }

        return this.hasReadNextLine;
    }

    private void endColumn() throws IOException {
        String var1 = "";
        int var2;
        if (this.startedColumn) {
            // columnBuffer用于存放上次读取到一半的数据，如果this.columnBuffer.Position，则直接从this.dataBuffer.ColumnStart读到分隔符即为字段值
            if (this.columnBuffer.Position == 0) {
                if (this.dataBuffer.ColumnStart < this.dataBuffer.Position) {
                    var2 = this.dataBuffer.Position - this.lastLetter.length();// 当前位置 减掉分隔符长度
                    if (this.userSettings.TrimWhitespace && !this.startedWithQualifier) {
                        // 删去字段结尾的空格或制表符
                        while (var2 >= this.dataBuffer.ColumnStart &&
                                (this.dataBuffer.Buffer[var2] == ' ' || this.dataBuffer.Buffer[var2] == '\t')) {
                            --var2;
                        }
                    }
                    //此处截取出从column起始位置到分隔符前一个位置的buffer，即为字段值
                    var1 = new String(this.dataBuffer.Buffer, this.dataBuffer.ColumnStart, var2 - this.dataBuffer.ColumnStart + 1);
                }
            } else {
                // 字段部分数据在缓冲区，则将当前databuffer读取的字段数据，继续刷到缓冲区，再从缓冲区中取字段值
                this.updateCurrentValue();
                var2 = this.columnBuffer.Position - this.lastLetter.length();
                if (this.userSettings.TrimWhitespace && !this.startedWithQualifier) {
                    while (var2 >= 0 && (this.columnBuffer.Buffer[var2] == ' ' || this.columnBuffer.Buffer[var2] == ' ')) {
                        --var2;
                    }
                }

                var1 = new String(this.columnBuffer.Buffer, 0, var2 + 1);
            }
        }

        //读取当前字段完毕
        this.columnBuffer.Position = 0;
        this.startedColumn = false;
        if (this.columnsCount >= 100000 && this.userSettings.SafetySwitch) {
            this.close();
            throw new IOException("Maximum column count of 100,000 exceeded in record " + NumberFormat.getIntegerInstance().format(this.currentRecord) + ". Set the SafetySwitch property to false" + " if you're expecting more than 100,000 columns per record to" + " avoid this error.");
        } else {
            //如果行字段读取数量 = 存字段数据的长度，对数组进行扩容，初始默认长度为10
            if (this.columnsCount == this.values.length) {
                var2 = this.values.length * 2;
                String[] var3 = new String[var2];
                System.arraycopy(this.values, 0, var3, 0, this.values.length);
                this.values = var3;
                boolean[] var4 = new boolean[var2];
                System.arraycopy(this.isQualified, 0, var4, 0, this.isQualified.length);
                this.isQualified = var4;
            }

            // 将读取字段添加到数组中
            this.values[this.columnsCount] = var1;
            this.isQualified[this.columnsCount] = this.startedWithQualifier;

            var1 = "";
            ++this.columnsCount;
        }
    }

    /**
     * dataBuffer缓冲区中数据处理完毕，尝试从输入流读取数据，并在读取数据前，
     * 将当前正在读的行数据和字段数据存入缓冲区（应该是为了处理当缓冲区读取截止位置位于字段中或者行中的情况）
     */
    private void checkDataLength() throws IOException {
        // 是否已经进行了初始化，若没有，检查file是否存在，读取file获取数据
        if (!this.initialized) {
            if (this.fileName != null) {
                this.inputStream = new BufferedReader(new InputStreamReader(new FileInputStream(this.fileName), this.charset), 4096);
            }
            this.charset = null;
            this.initialized = true;
        }

        this.updateCurrentValue();

        // 把当前行的数据存放行数据缓冲区中
        if (this.userSettings.CaptureRawRecord && this.dataBuffer.Count > 0) {
            // 如果行数据长度大于行数据缓冲区，则对缓冲区进行扩大，再进行复制
            if (this.rawBuffer.Buffer.length - this.rawBuffer.Position < this.dataBuffer.Count - this.dataBuffer.LineStart) {
                int var1 = this.rawBuffer.Buffer.length + Math.max(this.dataBuffer.Count - this.dataBuffer.LineStart, this.rawBuffer.Buffer.length);
                char[] var2 = new char[var1];
                System.arraycopy(this.rawBuffer.Buffer, 0, var2, 0, this.rawBuffer.Position);
                this.rawBuffer.Buffer = var2;
            }
            // 缓冲区大小足够，直接复制到缓冲区
            System.arraycopy(this.dataBuffer.Buffer, this.dataBuffer.LineStart, this.rawBuffer.Buffer, this.rawBuffer.Position,this.dataBuffer.Count - this.dataBuffer.LineStart);
            //移动缓冲区的position到下次写入位置
            this.rawBuffer.Position += this.dataBuffer.Count - this.dataBuffer.LineStart;
        }

        try {
            // 从输入流读取数据到dataBuffer
            this.dataBuffer.Count = this.inputStream.read(this.dataBuffer.Buffer, 0, this.dataBuffer.Buffer.length);
        } catch (IOException var3) {
            this.close();
            throw var3;
        }

        // 未读到数据或数据已读完，程序结束
        if (this.dataBuffer.Count == -1) {
            this.hasMoreData = false;
        }

        //重新读取数据后，对dataBuffer的处理标志符初始化
        this.dataBuffer.Position = 0;
        this.dataBuffer.LineStart = 0;
        this.dataBuffer.ColumnStart = 0;
    }

    /**
     * 在字段读取截止或缓冲区读取完毕需要刷新缓冲区时，将当前正在读取的字段值存入字段缓冲区中
     */
    private void updateCurrentValue() {
        // 判断当前字段是否处于读取状态，若是说明字段读取尚未完毕
        if (this.startedColumn && this.dataBuffer.ColumnStart < this.dataBuffer.Position) {
            // 如果字段缓冲区剩余容量不够存储当前字段已经读到的值，对缓冲区进行扩容
            if (this.columnBuffer.Buffer.length - this.columnBuffer.Position < this.dataBuffer.Position - this.dataBuffer.ColumnStart) {
                //扩容因子，待写入字段长度和 缓冲区长度的较大值
                int var1 = this.columnBuffer.Buffer.length + Math.max(this.dataBuffer.Position - this.dataBuffer.ColumnStart,this.columnBuffer.Buffer.length);
                char[] var2 = new char[var1];
                System.arraycopy(this.columnBuffer.Buffer, 0, var2, 0, this.columnBuffer.Position);
                this.columnBuffer.Buffer = var2;
            }
            System.arraycopy(this.dataBuffer.Buffer, this.dataBuffer.ColumnStart, this.columnBuffer.Buffer, this.columnBuffer.Position,this.dataBuffer.Position - this.dataBuffer.ColumnStart);
            //字段缓冲区的postition位置向后移动写入数据长度
            this.columnBuffer.Position += this.dataBuffer.Position - this.dataBuffer.ColumnStart;
        }
        this.dataBuffer.ColumnStart = this.dataBuffer.Position + 1;
    }

    private void endRecord() throws IOException {
        this.hasReadNextLine = true;
        ++this.currentRecord;
    }

    private void appendLetter(char var1) {
        if (this.columnBuffer.Position == this.columnBuffer.Buffer.length) {
            int var2 = this.columnBuffer.Buffer.length * 2;
            char[] var3 = new char[var2];
            System.arraycopy(this.columnBuffer.Buffer, 0, var3, 0, this.columnBuffer.Position);
            this.columnBuffer.Buffer = var3;
        }

        this.columnBuffer.Buffer[this.columnBuffer.Position++] = var1;
        this.dataBuffer.ColumnStart = this.dataBuffer.Position + 1;
    }

    //跳过当前行（当前行为注释时执行）
    public boolean skipLine() throws IOException {
        this.checkClosed();
        this.columnsCount = 0;
        boolean var1 = false;
        if (this.hasMoreData) {
            boolean var2 = false;

            do {
                if (this.dataBuffer.Position == this.dataBuffer.Count) {
                    this.checkDataLength();
                } else {
                    var1 = true;
                    char var3 = this.dataBuffer.Buffer[this.dataBuffer.Position];
                    if (var3 == '\r' || var3 == '\n') {
                        var2 = true;
                    }

                    this.lastLetter = String.valueOf(var3);
                    if (!var2) {
                        ++this.dataBuffer.Position;
                    }
                }
            } while (this.hasMoreData && !var2);

            this.columnBuffer.Position = 0;
            this.dataBuffer.LineStart = this.dataBuffer.Position + 1;
        }

        this.rawBuffer.Position = 0;
        this.rawRecord = "";
        return var1;
    }

    private class StaticSettings {
        public static final int MAX_BUFFER_SIZE = 1024;
        public static final int MAX_FILE_BUFFER_SIZE = 4096;
        public static final int INITIAL_COLUMN_COUNT = 10;
        public static final int INITIAL_COLUMN_BUFFER_SIZE = 50;

        private StaticSettings() {
        }
    }

    private class HeadersHolder {
        public String[] Headers = null;
        public int Length = 0;
        public HashMap IndexByName = new HashMap();

        public HeadersHolder() {
        }
    }

    private class Letters {
        public static final char LF = '\n';
        public static final char CR = '\r';
        public static final char QUOTE = '"';
        public static final char COMMA = ',';
        public static final char SPACE = ' ';
        public static final char TAB = '\t';
        public static final char POUND = '#';
        public static final char BACKSLASH = '\\';
        public static final char NULL = '\u0000';
        public static final char BACKSPACE = '\b';
        public static final char FORM_FEED = '\f';
        public static final char ESCAPE = '\u001b';
        public static final char VERTICAL_TAB = '\u000b';
        public static final char ALERT = '\u0007';

        private Letters() {
        }
    }

    private class ComplexEscape {
        private static final int UNICODE = 1;
        private static final int OCTAL = 2;
        private static final int DECIMAL = 3;
        private static final int HEX = 4;

        private ComplexEscape() {
        }
    }

    public MyCsvReader(String var1, String var2, Charset var3) throws FileNotFoundException {
        this.inputStream = null;
        this.fileName = null;
        this.userSettings = new MyCsvReader.UserSettings();
        this.charset = null;
        this.useCustomRecordDelimiter = false;
        this.dataBuffer = new MyCsvReader.DataBuffer();
        this.columnBuffer = new MyCsvReader.ColumnBuffer();
        this.rawBuffer = new MyCsvReader.RawRecordBuffer();
        this.isQualified = null;
        this.rawRecord = "";
        this.headersHolder = new MyCsvReader.HeadersHolder();
        this.startedColumn = false;
        this.startedWithQualifier = false;
        this.hasMoreData = true;
        this.lastLetter = "0";
        this.hasReadNextLine = false;
        this.columnsCount = 0;
        this.currentRecord = 0L;
        this.values = new String[10];
        this.initialized = false;
        this.closed = false;
        if (var1 == null) {
            throw new IllegalArgumentException("Parameter fileName can not be null.");
        } else if (var3 == null) {
            throw new IllegalArgumentException("Parameter charset can not be null.");
        } else if (!(new File(var1)).exists()) {
            throw new FileNotFoundException("File " + var1 + " does not exist.");
        } else {
            this.fileName = var1;
            this.userSettings.Delimiter = var2;
            this.charset = var3;
            this.isQualified = new boolean[this.values.length];
        }
    }

    public MyCsvReader(String var1, String var2) throws FileNotFoundException {
        this(var1, var2, Charset.forName("ISO-8859-1"));
    }

    public MyCsvReader(String var1) throws FileNotFoundException {
        this(var1, ",");
    }

    public MyCsvReader(Reader var1, String var2) {
        this.inputStream = null;
        this.fileName = null;
        this.userSettings = new MyCsvReader.UserSettings();
        this.charset = null;
        this.useCustomRecordDelimiter = false;
        this.dataBuffer = new MyCsvReader.DataBuffer();
        this.columnBuffer = new MyCsvReader.ColumnBuffer();
        this.rawBuffer = new MyCsvReader.RawRecordBuffer();
        this.isQualified = null;
        this.rawRecord = "";
        this.headersHolder = new MyCsvReader.HeadersHolder();
        this.startedColumn = false;
        this.startedWithQualifier = false;
        this.hasMoreData = true;
        this.lastLetter = "0";
        this.hasReadNextLine = false;
        this.columnsCount = 0;
        this.currentRecord = 0L;
        this.values = new String[10];
        this.initialized = false;
        this.closed = false;
        if (var1 == null) {
            throw new IllegalArgumentException("Parameter inputStream can not be null.");
        } else {
            this.inputStream = var1;
            this.userSettings.Delimiter = var2;
            this.initialized = true;
            this.isQualified = new boolean[this.values.length];
        }
    }

    public MyCsvReader(Reader var1) {
        this(var1, ",");
    }

    public MyCsvReader(InputStream var1, String var2, Charset var3) {
        this((Reader) (new InputStreamReader(var1, var3)), var2);
    }

    public MyCsvReader(InputStream var1, Charset var2) {
        this((Reader) (new InputStreamReader(var1, var2)));
    }

    public boolean getCaptureRawRecord() {
        return this.userSettings.CaptureRawRecord;
    }

    public void setCaptureRawRecord(boolean var1) {
        this.userSettings.CaptureRawRecord = var1;
    }

    public String getRawRecord() {
        return this.rawRecord;
    }

    public boolean getTrimWhitespace() {
        return this.userSettings.TrimWhitespace;
    }

    public void setTrimWhitespace(boolean var1) {
        this.userSettings.TrimWhitespace = var1;
    }

    public String getDelimiter() {
        return this.userSettings.Delimiter;
    }

    public void setDelimiter(String var1) {
        this.userSettings.Delimiter = var1;
    }

    public char getRecordDelimiter() {
        return this.userSettings.RecordDelimiter;
    }

    public void setRecordDelimiter(char var1) {
        this.useCustomRecordDelimiter = true;
        this.userSettings.RecordDelimiter = var1;
    }

    public char getTextQualifier() {
        return this.userSettings.TextQualifier;
    }

    public void setTextQualifier(char var1) {
        this.userSettings.TextQualifier = var1;
    }

    public boolean getUseTextQualifier() {
        return this.userSettings.UseTextQualifier;
    }

    public void setUseTextQualifier(boolean var1) {
        this.userSettings.UseTextQualifier = var1;
    }

    public char getComment() {
        return this.userSettings.Comment;
    }

    public void setComment(char var1) {
        this.userSettings.Comment = var1;
    }

    public boolean getUseComments() {
        return this.userSettings.UseComments;
    }

    public void setUseComments(boolean var1) {
        this.userSettings.UseComments = var1;
    }

    public boolean getSkipEmptyRecords() {
        return this.userSettings.SkipEmptyRecords;
    }

    public void setSkipEmptyRecords(boolean var1) {
        this.userSettings.SkipEmptyRecords = var1;
    }

    public boolean getSafetySwitch() {
        return this.userSettings.SafetySwitch;
    }

    public void setSafetySwitch(boolean var1) {
        this.userSettings.SafetySwitch = var1;
    }

    public int getColumnCount() {
        return this.columnsCount;
    }

    public long getCurrentRecord() {
        return this.currentRecord - 1L;
    }

    public int getHeaderCount() {
        return this.headersHolder.Length;
    }

    public String[] getHeaders() throws IOException {
        this.checkClosed();
        if (this.headersHolder.Headers == null) {
            return null;
        } else {
            String[] var1 = new String[this.headersHolder.Length];
            System.arraycopy(this.headersHolder.Headers, 0, var1, 0, this.headersHolder.Length);
            return var1;
        }
    }

    public void setHeaders(String[] var1) {
        this.headersHolder.Headers = var1;
        this.headersHolder.IndexByName.clear();
        if (var1 != null) {
            this.headersHolder.Length = var1.length;
        } else {
            this.headersHolder.Length = 0;
        }

        for (int var2 = 0; var2 < this.headersHolder.Length; ++var2) {
            this.headersHolder.IndexByName.put(var1[var2], new Integer(var2));
        }

    }

    public String[] getValues() throws IOException {
        this.checkClosed();
        String[] var1 = new String[this.columnsCount];
        System.arraycopy(this.values, 0, var1, 0, this.columnsCount);
        return var1;
    }

    public String get(int var1) throws IOException {
        this.checkClosed();
        return var1 > -1 && var1 < this.columnsCount ? this.values[var1] : "";
    }

    public String get(String var1) throws IOException {
        this.checkClosed();
        return this.get(this.getIndex(var1));
    }

    public static MyCsvReader parse(String var0) {
        if (var0 == null) {
            throw new IllegalArgumentException("Parameter data can not be null.");
        } else {
            return new MyCsvReader(new StringReader(var0));
        }
    }

    public boolean readHeaders() throws IOException {
        boolean var1 = this.readRecord();
        this.headersHolder.Length = this.columnsCount;
        this.headersHolder.Headers = new String[this.columnsCount];

        for (int var2 = 0; var2 < this.headersHolder.Length; ++var2) {
            String var3 = this.get(var2);
            this.headersHolder.Headers[var2] = var3;
            this.headersHolder.IndexByName.put(var3, new Integer(var2));
        }

        if (var1) {
            --this.currentRecord;
        }

        this.columnsCount = 0;
        return var1;
    }

    public String getHeader(int var1) throws IOException {
        this.checkClosed();
        return var1 > -1 && var1 < this.headersHolder.Length ? this.headersHolder.Headers[var1] : "";
    }

    public boolean isQualified(int var1) throws IOException {
        this.checkClosed();
        return var1 < this.columnsCount && var1 > -1 ? this.isQualified[var1] : false;
    }

    public int getIndex(String var1) throws IOException {
        this.checkClosed();
        Object var2 = this.headersHolder.IndexByName.get(var1);
        return var2 != null ? (Integer) var2 : -1;
    }

    public boolean skipRecord() throws IOException {
        this.checkClosed();
        boolean var1 = false;
        if (this.hasMoreData) {
            var1 = this.readRecord();
            if (var1) {
                --this.currentRecord;
            }
        }

        return var1;
    }

    public void close() {
        if (!this.closed) {
            this.close(true);
            this.closed = true;
        }

    }

    private void close(boolean var1) {
        if (!this.closed) {
            if (var1) {
                this.charset = null;
                this.headersHolder.Headers = null;
                this.headersHolder.IndexByName = null;
                this.dataBuffer.Buffer = null;
                this.columnBuffer.Buffer = null;
                this.rawBuffer.Buffer = null;
            }

            try {
                if (this.initialized) {
                    this.inputStream.close();
                }
            } catch (Exception var3) {
                ;
            }

            this.inputStream = null;
            this.closed = true;
        }

    }

    private void checkClosed() throws IOException {
        if (this.closed) {
            throw new IOException("This instance of the CsvReader class has already been closed.");
        }
    }

    @Override
    protected void finalize() {
        this.close(false);
    }

    //16进制转10进制
    private static char hexToDec(char var0) {
        char var1;
        if (var0 >= 'a') {
            var1 = (char) (var0 - 97 + 10);
        } else if (var0 >= 'A') {
            var1 = (char) (var0 - 65 + 10);
        } else {
            var1 = (char) (var0 - 48);
        }

        return var1;
    }
}
