package main

import java.io.FileWriter
import java.util.concurrent.TimeUnit

import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.collection.mutable
/**
  * Created by MA on 2017/3/22.
  */
object runtpcds {
  def main(args: Array[String]): Unit = {

    val sparkConf = new SparkConf().setAppName("tpcds")

    sparkConf.set("spark.streaming.concurrentJobs", "1")

    val spark = SparkSession.builder()
      .config(sparkConf)
      .getOrCreate()

//    "call_center",
//    "customer_address",
//    "household_demographics",
//    "item",
//    "store_returns",
//    "web_returns",
//    "catalog_page",
//    "customer",
//    "promotion",
//    "store_sales",
//    "web_sales",
//    "catalog_returns",
//    "customer_demographics",
//    "income_band",
//    "reason",
//    "time_dim",
//    "web_site",
//    "catalog_sales",
//    "date_dim",
//    "ship_mode",
//    "warehouse",
//    "inventory",
//    "store",
//    "web_page"
    var csvs = Seq( "call_center",
                    "catalog_page",
                    "catalog_returns",
                    "catalog_sales",
                    "customer_address",
                    "customer",
                    "customer_demographics",
                    "date_dim",
                    "household_demographics",
                    "income_band",
                    "inventory",
                    "item",
                    "promotion",
                    "reason",
                    "ship_mode",
                    "store",
                    "store_returns",
                    "store_sales",
                    "time_dim",
                    "warehouse",
                    "web_page",
                    "web_returns",
                    "web_sales",
                    "web_site")


    csvs foreach( c => {
      var path=args(0)+"/"+c +".dat"
      var df = spark.read
        .option("header", "true")
        .option("inferSchema", "true")
        .csv(path)

      df.createOrReplaceTempView(c)
      df.sqlContext.cacheTable(c)
      df.count()
    })

    Log.info("runing")
    //TimeUnit.MINUTES.sleep(5)

    var query =  new Tpcds_1_4_Queries()
    var q = query.tpcds1_4Queries
    var data:DataFrame = null

    val succeeded = mutable.ArrayBuffer.empty[String]

    q.foreach( qzz => {
      //println(s"Query: ${qzz._1}")
      val start = System.currentTimeMillis()
      val df = spark.sql(qzz._2)
      var failed = false
      val jobgroup = s"benchmark ${qzz._1}"

      val t = new Thread("query runner") {
        override def run(): Unit = {
          try {
            spark.sparkContext.setJobGroup(jobgroup, jobgroup, true)
            df.count()
          } catch {
            case e: Exception =>
              println("Failed to run: " + e)
              failed = true
          }
        }
      }

      t.setDaemon(true)
      t.start()
      t.join(100000000)
      if (t.isAlive) {
        println("Timeout after 100 seconds")
        spark.sparkContext.cancelJobGroup(jobgroup)
        t.interrupt()
      } else {
        if (!failed) {
          succeeded += qzz._1

          var time =System.currentTimeMillis() - start
          println(qzz._1+","+time/1000.00)
        }
      }





//      var totaltime: Long = 0
//      var count = 0
//      for (i <- 1 to 3) {
//        var failed = false
//        val start = System.currentTimeMillis()
//        val df = spark.sql(qzz._2)
//
//        try {
//          df.collect()
//        } catch {
//          case e: Exception =>
//            Log.error("Failed to run: " + e)
//            failed = true
//        }
//        if (!failed) {
//          count = count+1
//          totaltime = totaltime + System.currentTimeMillis() - start
//        }
//      }
//      var avetime = totaltime/count
//      println(s"   [${qzz._1}] ${avetime} ms")

    }
    )

    println(s"Ran ${succeeded.size} out of ${q.length}")
    println(succeeded.map("\"" + _ + "\""))
  }
}
