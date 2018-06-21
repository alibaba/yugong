package com.taobao.yugong.data;

import com.google.common.base.Throwables;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.*;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Fill MySQL Data
 *
 * @author caobin
 * @version 1.0 2017.07.10
 */
public class DataGenerator {

    private static final int REC_THRESHOLDS = 10;

    static {

        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            Throwables.propagate(e);
        }
    }

    private static Connection getConnection() {
        try {
            return DriverManager.getConnection("jdbc:mysql://192.168.177.70:3306/test_mysql", "user_acct", "!@#Qaz");
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }


    private static void fillShopOrderDetail(Integer records) throws Exception {
        Queue<Integer> queue = null;
        if (records > REC_THRESHOLDS) {
            int times = records / REC_THRESHOLDS;
            queue = new ArrayBlockingQueue<Integer>(times + 1);
            while (times-- > 0) {
                queue.add(REC_THRESHOLDS);
            }
            int remain = records % REC_THRESHOLDS;
            if (remain > 0) {
                queue.add(remain);
            }
        } else {
            queue = new ArrayBlockingQueue<Integer>(1);
            queue.add(records);
        }

        while (queue != null) {
            records = queue.poll();
            if (records == null) break;
            Connection conn = getConnection();
            PreparedStatement pstmt = null;
            try {
                pstmt = conn.prepareStatement("insert into shop_order_detail(order_id, product_id, quantity, unit_cost, is_refunded, product_cate, discount_amount, timestamp) values (?,?,?,?,?,?,?,?)");
                while (records-- > 0) {
                    Random random = new Random();
                    pstmt.setInt(1, Math.abs(random.nextInt(90000000)));
                    pstmt.setInt(2, Math.abs(random.nextInt(60000000)));
                    pstmt.setInt(3, Math.abs(random.nextInt(50000)));
                    pstmt.setBigDecimal(4, new BigDecimal(random.nextFloat(), new MathContext(10, RoundingMode.CEILING)).abs());
                    pstmt.setBoolean(5, records % 2 == 0);
                    pstmt.setInt(6, 4);
                    pstmt.setBigDecimal(7, new BigDecimal(random.nextFloat(), new MathContext(10, RoundingMode.CEILING)).abs());
                    pstmt.setDate(8, new Date(System.currentTimeMillis()));

                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            } finally {
                if (pstmt != null) pstmt.close();
                if (conn != null) conn.close();
            }
        }

    }


    private static void fillShopOrderDetailMulti(Integer records) throws Exception {
        Queue<Integer> queue = null;
        if (records > REC_THRESHOLDS) {
            int times = records / REC_THRESHOLDS;
            queue = new ArrayBlockingQueue<Integer>(times + 1);
            while (times-- > 0) {
                queue.add(REC_THRESHOLDS);
            }
            int remain = records % REC_THRESHOLDS;
            if (remain > 0) {
                queue.add(remain);
            }
        } else {
            queue = new ArrayBlockingQueue<Integer>(1);
            queue.add(records);
        }

        while (queue != null) {
            records = queue.poll();
            if (records == null) break;
            Connection conn = getConnection();
            PreparedStatement pstmt = null;
            try {
                pstmt = conn.prepareStatement("insert into shop_order_detail_multi(order_id, multi_product_id, product_id, unit_cost) values (?,?,?,?)");
                while (records-- > 0) {
                    Random random = new Random();
                    pstmt.setInt(1, Math.abs(random.nextInt(800000000)));
                    pstmt.setInt(2, Math.abs(random.nextInt(700000000)));
                    pstmt.setInt(3, Math.abs(random.nextInt(10000)));
                    pstmt.setBigDecimal(4, new BigDecimal(random.nextFloat(), new MathContext(10, RoundingMode.CEILING)).abs());
                    pstmt.addBatch();

                }
                pstmt.executeBatch();

            } finally {
                if (pstmt != null) pstmt.close();
                if (conn != null) conn.close();
            }
        }
    }

    private static void fillNormalDetai(Integer records) throws Exception {
        Queue<Integer> queue = null;
        if (records > REC_THRESHOLDS) {
            int times = records / REC_THRESHOLDS;
            queue = new ArrayBlockingQueue<Integer>(times + 1);
            while (times-- > 0) {
                queue.add(REC_THRESHOLDS);
            }
            int remain = records % REC_THRESHOLDS;
            if (remain > 0) {
                queue.add(remain);
            }
        } else {
            queue = new ArrayBlockingQueue<Integer>(1);
            queue.add(records);
        }

        while (queue != null) {
            records = queue.poll();
            if (records == null) break;
            Connection conn = getConnection();
            PreparedStatement pstmt = null;
            try {
                pstmt = conn.prepareStatement("insert into normal_detail(display_name, amount) values (?,?)");
                while (records-- > 0) {
                    Random random = new Random();
                    pstmt.setString(1, "N" + Math.abs(random.nextInt(800000000)));
                    pstmt.setString(2, Math.abs(random.nextInt(700000000))+"");
                    pstmt.addBatch();

                }
                pstmt.executeBatch();

            } finally {
                if (pstmt != null) pstmt.close();
                if (conn != null) conn.close();
            }
        }
    }


    public static void fillData(Tables tables, int recordsPerThread, int threads) throws Exception {
        CyclicBarrier cyclicBarrier = new CyclicBarrier(threads);
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executorService.execute(new DataFiller(tables, cyclicBarrier, recordsPerThread));
        }

        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.HOURS);
    }



    enum Tables {
        ShopOrderDetailMulti,

        ShopOrderDetail,

        NormalDetail
    }

    static class DataFiller implements Runnable {

        private Tables tables;

        private CyclicBarrier cyclicBarrier;

        private int records;

        public DataFiller(Tables tables, CyclicBarrier cyclicBarrier, int records) {
            this.tables = tables;
            this.cyclicBarrier = cyclicBarrier;
            this.records = records;
        }

        @Override
        public void run() {
            try {
                switch (tables) {
                    case ShopOrderDetail:
                        fillShopOrderDetail(records);
                        break;
                    case ShopOrderDetailMulti:
                        fillShopOrderDetailMulti(records);
                        break;
                    case NormalDetail:
                        fillNormalDetai(records);
                        break;
                    default:
                }
                cyclicBarrier.await();
            } catch (Exception e) {
                Throwables.propagate(e);
            }

        }
    }

    public static void main(String[] args) throws Exception {
         //fillData(Tables.ShopOrderDetail, 100000, 10);
         fillData(Tables.ShopOrderDetailMulti, 10000, 20);
         fillData(Tables.NormalDetail, 100000, 20);
    }
}
