package org.conan.myhadoop.recommend;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.*;
import org.conan.myhadoop.hdfs.HdfsDAO;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class Step1 {

    public static class Step1_ToItemPreMapper
    		extends MapReduceBase
    		implements Mapper<Object, Text, IntWritable, Text> {
    	
        private final static IntWritable k = new IntWritable();
        private final static Text v = new Text();
        /*
         * 这里读取的文件每一行一条记录：用户ID,电影ID,评分值
         * 该map方法实现以userID为键，itemID和评分为值输出
         */
        @Override
        public void map(Object key, Text value,
                        OutputCollector<IntWritable, Text> output, Reporter reporter)
        				throws IOException {
            String[] tokens = Recommend.DELIMITER.split(value.toString());
            int userID = Integer.parseInt(tokens[0]);
            String itemID = tokens[1];
            String pref = tokens[2];
            k.set(userID);
            v.set(itemID + ":" + pref);
            output.collect(k, v);
        }
    }

    public static class Step1_ToUserVectorReducer
    	extends MapReduceBase implements Reducer<IntWritable,
    		Text, IntWritable, Text> {
        private final static Text v = new Text();
        /*
         * 以userID为键，以相同userID的键对应的值以逗号分割拼接，写入到文件中
         */
        @Override
        public void reduce(IntWritable key, Iterator<Text> values,
                           OutputCollector<IntWritable, Text> output,
                           Reporter reporter) throws IOException {
            StringBuilder sb = new StringBuilder();
            while (values.hasNext()) {
                sb.append("," + values.next());
            }
            v.set(sb.toString().replaceFirst(",", ""));
            output.collect(key, v);
        }
    }

    public static void run(Map<String, String> path) throws IOException {
        JobConf conf = Recommend.config();

        String input = path.get("Step1Input");
        String output = path.get("Step1Output");

        HdfsDAO hdfs = new HdfsDAO(Recommend.HDFS, conf);
//        hdfs.rmr(output);
        hdfs.rmr(input);
        hdfs.mkdirs(input);
        hdfs.copyFile(path.get("data"), input);

        conf.setMapOutputKeyClass(IntWritable.class);
        conf.setMapOutputValueClass(Text.class);

        conf.setOutputKeyClass(IntWritable.class);
        conf.setOutputValueClass(Text.class);

        conf.setMapperClass(Step1_ToItemPreMapper.class);
        conf.setCombinerClass(Step1_ToUserVectorReducer.class);
        conf.setReducerClass(Step1_ToUserVectorReducer.class);

        conf.setInputFormat(TextInputFormat.class);
        conf.setOutputFormat(TextOutputFormat.class);

        FileInputFormat.setInputPaths(conf, new Path(input));
        FileOutputFormat.setOutputPath(conf, new Path(output));

        RunningJob job = JobClient.runJob(conf);
        while (!job.isComplete()) {
            job.waitForCompletion();
        }
    }

}