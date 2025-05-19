# 🧾 Discount Engine in Scala

This is a Scala-based **rules engine** that calculates discounts for retail transactions based on product type, expiry date, quantity, special dates, purchase method (via app), and payment method (e.g., Visa). The engine reads transactions from a CSV file, calculates the best applicable discount, and writes the results to a PostgreSQL database.

---

## 📌 Features

- ✅ Multiple discount strategies:
  - Expiry-based discounts
  - Product-specific discounts
  - Special date promotions
  - Quantity-based discounts
  - App-exclusive promotions
  - Visa payment discount
- 📊 Best two discounts are averaged
- 🗃 Stores final results in a PostgreSQL database
- 📁 Transaction logs saved to `rules_engine.log`

---

## 🛠 How It Works

1. Reads transactions from a CSV file (`transactions.csv`).
2. Parses each line into a `Transaction` object.
3. Applies **all discount rules** and selects the **top two**.
4. Calculates the final price after applying the discount.
5. Saves the transaction to a PostgreSQL `Orders` table.
6. Logs the applied discounts for traceability.

---

## 🧾 Discount Rules

| Rule Type         | Description |
|------------------|-------------|
| Expiry Discount  | 1% for each day under 30 before expiry |
| Product Discount | 10% for "cheese", 5% for "wine" |
| Special Date     | 50% if transaction is on March 23 |
| Quantity         | 5% (6-9), 7% (10-14), 10% (15+) |
| App-Based        | Discount increases with rounded quantity (to nearest 5) |
| Visa Discount    | 5% if payment method contains "visa" |

Only the **top two discounts** are averaged and applied.

---

## ⚙️ Prerequisites

- Scala 3.x
- SBT (Scala Build Tool)
- PostgreSQL (running locally or remotely)
- Java JDK 11+

---

## Sample `transactions.csv` Format

```csv
timestamp,productName,expiryDate,quantity,unitPrice,viaApp,paymentMethod
2025-03-23,Cheddar Cheese,2025-04-01,8,50.0,true,visa
2025-04-10,Red Wine,2025-06-15,4,120.0,false,mastercard
```

---

## 🚀 Running the Project

1. **Clone the repository:**
   ```bash
   git clone https://github.com/AhmedEladwy99/discountengine.git
   cd discountengine
   ```

2. **Prepare your PostgreSQL database:**
   - Ensure PostgreSQL is running.
   - Create a database named `postgres`.
   - Update `username` and `password` in `main.scala` if needed.

3. **Run the project with SBT:**
   ```bash
   sbt run
   ```

---

## 🗂 Database Table

This project automatically creates a table:

```sql
CREATE TABLE IF NOT EXISTS Orders (
  Order_Date DATE,
  Product_Name TEXT,
  Expiry_Date DATE,
  Quantity INTEGER,
  Unit_Price DOUBLE PRECISION,
  Discount DOUBLE PRECISION,
  Final_Price DOUBLE PRECISION
);
```

---

## 📓 Logging

All operations and applied discounts are recorded in:

```
rules_engine.log
```

Example log entry:

```
2025-05-18T14:12:33   INFO   Applied discount 25.0% to Cheddar Cheese
```

---

## 📬 Contact

For questions or collaboration, feel free to reach out at [ahmedeladwy9800@gmail.com].

---

## 📝 License

This project is open-source and available under the MIT License.

