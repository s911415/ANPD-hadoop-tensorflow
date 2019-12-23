package nctu.cs.oss.hw2;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import java.io.IOException;

/**
 * Created by wcl on 2019/12/23.
 */
public final class mapreduce {
    public static class WholeFileInputFormat extends FileInputFormat<NullWritable, BytesWritable> {
        protected boolean isSplitable(JobContext context, Path filename) {
            return false;
        }

        @Override
        public RecordReader<NullWritable, BytesWritable> createRecordReader(InputSplit inputSplit, TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
            return new WholeFileRecordReader();
        }
    }

    public static class WholeFileRecordReader extends RecordReader<NullWritable, BytesWritable> {

        private FileSplit fileSplit;
        private Configuration conf;
        private boolean processed = false;

        private NullWritable key = NullWritable.get();
        private BytesWritable value = new BytesWritable();

        public void initialize(InputSplit inputSplit, TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
            this.fileSplit = (FileSplit) inputSplit;
            this.conf = taskAttemptContext.getConfiguration();
        }

        public boolean nextKeyValue() throws IOException {
            if (!processed) {
                byte[] contents = new byte[(int) fileSplit.getLength()];

                Path file = fileSplit.getPath();
                FileSystem fs = file.getFileSystem(conf);

                FSDataInputStream in = null;
                try {
                    in = fs.open(file);
                    IOUtils.readFully(in, contents, 0, contents.length);
                    value.set(contents, 0, contents.length);
                } finally {
                    IOUtils.closeStream(in);
                }
                processed = true;
                return true;
            }
            return false;
        }

        @Override
        public NullWritable getCurrentKey() throws IOException, InterruptedException {
            return key;
        }

        @Override
        public BytesWritable getCurrentValue() throws IOException, InterruptedException {
            return value;
        }

        @Override
        public float getProgress() throws IOException, InterruptedException {
            return processed ? 1.0f : 0.0f;
        }

        @Override
        public void close() throws IOException {
            // do nothing
        }
    }
}
