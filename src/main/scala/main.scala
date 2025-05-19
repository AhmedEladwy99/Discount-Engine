
package discountengine

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.io.Source
import java.io.PrintWriter
import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}

object main {
// This case class defines the data format for each transaction
  case class Transaction(
                          timestamp: LocalDate,
                          productName: String,
                          expiryDate: LocalDate,
                          quantity: Int,
                          unitPrice: Double,
                          viaApp: Boolean,
                          paymentMethod: String
                        )

  def parseLine(line: String): Transaction =
    line.split(",").map(_.trim).toList match {
      case ts :: name :: expiry :: qty :: price :: app :: payment :: _ =>
        Transaction(
          timestamp = LocalDate.parse(ts.take(10), DateTimeFormatter.ofPattern("yyyy-MM-dd")),
          productName = name,
          expiryDate = LocalDate.parse(expiry.take(10), DateTimeFormatter.ofPattern("yyyy-MM-dd")),
          quantity = qty.toInt,
          unitPrice = price.toDouble,
          viaApp = app.equalsIgnoreCase("true"),
          paymentMethod = payment.toLowerCase
        )
      case _ => throw new IllegalArgumentException("Invalid CSV line")
    }
  // Converts each line in a CSV file into a Transaction object
  def readTransactions(lines: List[String]): List[Transaction] =
    lines.map(parseLine)

  //  Comparing to purchase date - if the product has less than 30 days left until its expiration date the customer is still eligible for a discount
  //  Calculation method
  //  Each day less than 30 days = 1% discount and so on

  def expiryDiscount(tx: Transaction): Double = {
    val daysRemaining = ChronoUnit.DAYS.between(tx.timestamp, tx.expiryDate)
    if (daysRemaining > 0 && daysRemaining < 30)
      30 - daysRemaining.toDouble
    else
      0.0
  }


  // Calculates a discount based on the product name the function returns:
  // - 10% discount if the product name contains "cheese"
  // - 5% discount if the product name contains "wine"
  // - 0% other options
  // - getOrElse ensures a default value of 0.0 if the productName is null.

  def productTypeDiscount(tx: Transaction): Double =
    Option(tx.productName.toLowerCase).map {
      case name if name.contains("cheese") => 10.0
      case name if name.contains("wine") => 5.0
      case _ => 0.0
    }.getOrElse(0.0)


  // Returns a discount (50%) if the transaction date is March 23rd

  def specialDateDiscount(tx: Transaction): Double =
    Option(tx.timestamp).filter(d => d.getMonthValue == 3 && d.getDayOfMonth == 23).map(_ => 50.0).getOrElse(0.0)

  // Applies a quantity discount :
  // - 5% for quantities between 6 and 9
  // - 7% for quantities between 10 and 14
  // - 10% for quantities greater than 15
  // - 0% for all other cases

  def quantityDiscount(tx: Transaction): Double = tx.quantity match {
    case q if q >= 6 && q <= 9 => 5.0
    case q if q >= 10 && q <= 14 => 7.0
    case q if q > 15 => 10.0
    case _ => 0.0
  }


  // App-specific discount logic, only applied if the transaction was made via the mobile app.
  // Quantity is rounded to the nearest multiple of 5 using ceiling rounding logic.

  def appDiscount(tx: Transaction): Double = {
    if (!tx.viaApp) return 0.0
    val rounded = ((tx.quantity + 4) / 5) * 5
    rounded match {
      case q if q <= 5 => 5.0
      case q if q <= 10 => 10.0
      case q if q <= 15 => 15.0
      case q => (q / 5) * 5.0
    }
  }

  // 5% discount for visa
  def visaDiscount(tx: Transaction): Double =
    if (tx.paymentMethod.contains("visa")) 5.0 else 0.0

  //  best two discounts
  def calculateBestDiscount(tx: Transaction): Double =
    List(
      expiryDiscount(tx),
      productTypeDiscount(tx),
      specialDateDiscount(tx),
      quantityDiscount(tx),
      appDiscount(tx),
      visaDiscount(tx)
    ).filter(_ > 0).sorted(using summon[Ordering[Double]].reverse) match {
      case a :: b :: _ => (a + b) / 2
      case a :: Nil => a
      case _ => 0.0
    }

  // Calculates the total price after applying the discount.

  def calculateFinalPrice(tx: Transaction, discount: Double): Double =
    tx.unitPrice * tx.quantity * (1 - discount / 100)


//  Inserts the transaction, applied discount, and final price into the database

  def writeToDatabase(tx: Transaction, discount: Double, finalPrice: Double)(using connection: Connection): Unit = {
    val sql =
      """
        |INSERT INTO Orders (Order_Date, Product_Name, Expiry_Date, Quantity, Unit_Price, Discount, Final_Price)
        |VALUES (?, ?, ?, ?, ?, ?, ?)
        |""".stripMargin

    val stmt: PreparedStatement = connection.prepareStatement(sql)
    stmt.setDate(1, java.sql.Date.valueOf(tx.timestamp))
    stmt.setString(2, tx.productName)
    stmt.setDate(3, java.sql.Date.valueOf(tx.expiryDate))
    stmt.setInt(4, tx.quantity)
    stmt.setDouble(5, tx.unitPrice)
    stmt.setDouble(6, discount)
    stmt.setDouble(7, finalPrice)
    stmt.executeUpdate()
    stmt.close()
  }


  // 1. Applies the best discount
  // 2. Logs the operation
  // 3. Writes the result to the database

  def processTransactions(txs: List[Transaction], logger: PrintWriter)(using connection: Connection): Unit = {
    def recurse(list: List[Transaction]): Unit = list match {
      case Nil => ()
      case head :: tail =>
        val discount = calculateBestDiscount(head)
        val finalPrice = calculateFinalPrice(head, discount)

        logger.println(s"${LocalDateTime.now()}   INFO   Applied discount $discount% to ${head.productName}")
        println(s"${head.productName}: Discount = $discount%, Final Price = $finalPrice")

        writeToDatabase(head, discount, finalPrice)
        recurse(tail)
    }
    recurse(txs)
  }


//  Entry point of the program

  // 1. Reads transaction CSV
  // 2. Establishes DB connection
  // 3. Creates the Orders table if not exists
  // 4. Calls the processing function
  def main(args: Array[String]): Unit = {
    val (lines, logger) = (
      Source.fromFile("./transactions.csv").getLines().toList.drop(1),
      new PrintWriter("rules_engine.log")
    )

    val txs = readTransactions(lines)

    val url = "jdbc:postgresql://localhost:5432/postgres"
    val username = "admin"
    val password = "admin123"

    var connection: Connection = null

    try {
      connection = DriverManager.getConnection(url, username, password)
      println(" Connected to PostgreSQL successfully!")

        val createTableSQL =
        """
          |CREATE TABLE IF NOT EXISTS Orders (
          |  Order_Date DATE,
          |  Product_Name TEXT,
          |  Expiry_Date DATE,
          |  Quantity INTEGER,
          |  Unit_Price DOUBLE PRECISION,
          |  Discount DOUBLE PRECISION,
          |  Final_Price DOUBLE PRECISION
          |)
          |""".stripMargin

      val stmt = connection.createStatement()
      stmt.executeUpdate(createTableSQL)
      stmt.close()

      given Connection = connection
      processTransactions(txs, logger)

    } catch {
      case e: Exception => e.printStackTrace()
    } finally {
      if (connection != null) connection.close()
      logger.close()
    }
  }


//  method to ensure resources are automatically closed after use
  def using[A <: AutoCloseable, B](resource: A)(f: A => B): B =
    try f(resource) finally resource.close()
}
