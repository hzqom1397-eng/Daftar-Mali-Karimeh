package ir.karimeh.finance

import android.app.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private const val GREEN = 0xFF145A42.toInt()
private const val GOLD = 0xFFC6A04A.toInt()
private const val CREAM = 0xFFF7F4EA.toInt()
private const val DARK = 0xFF202820.toInt()

data class Tx(
    val id: Long,
    val kind: String,
    val amount: Long,
    val category: String,
    val note: String,
    val date: String,
    val unit: String = "",
    val quantity: Double = 0.0,
    val purchasePrice: Long = 0L,
    val salePrice: Long = 0L
)

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("karimeh_finance", MODE_PRIVATE)
    }

    private val txs = mutableListOf<Tx>()
    private lateinit var headerDate: TextView
    private lateinit var summary: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var pageContainer: FrameLayout
    private lateinit var bottomNav: LinearLayout

    private var currentPage = "home"

    private var chosenDate = Jalali.today()
    private val handler = Handler(Looper.getMainLooper())

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = GREEN
        window.navigationBarColor = GREEN
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        loadData()
        buildUi()
        refresh()
        handler.post(clockRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockRunnable)
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CREAM)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(GREEN)
        }

        header.addView(label("دفتر مالی کریمه", 23, Color.WHITE, true))
        headerDate = label("", 13, Color.WHITE)
        header.addView(headerDate)
        val goldStripe = View(this).apply { setBackgroundColor(GOLD) }
        header.addView(goldStripe, LinearLayout.LayoutParams(-1, dp(3)))
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        pageContainer = FrameLayout(this).apply {
            setBackgroundColor(CREAM)
        }
        root.addView(
            pageContainer,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(
            bottomNav,
            LinearLayout.LayoutParams(-1, dp(64))
        )

        setContentView(root)
        buildBottomNavigation()
        showPage("home")
    }

    private fun buildBottomNavigation() {
        bottomNav.removeAllViews()

        val items = listOf(
            Triple("home", "خانه", "⌂"),
            Triple("transactions", "تراکنش‌ها", "＋"),
            Triple("accounts", "حساب‌ها", "▤"),
            Triple("reports", "گزارش‌ها", "▥")
        )

        items.forEach { item ->
            val button = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(4), dp(2), dp(4))
                isClickable = true
                isFocusable = true
                setOnClickListener { showPage(item.first) }
            }

            val icon = TextView(this).apply {
                text = item.third
                textSize = 21f
                gravity = Gravity.CENTER
                setTextColor(if (currentPage == item.first) GREEN else Color.GRAY)
            }
            val title = TextView(this).apply {
                text = item.second
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(null, if (currentPage == item.first) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (currentPage == item.first) GREEN else Color.GRAY)
            }

            button.addView(icon)
            button.addView(title)
            bottomNav.addView(
                button,
                LinearLayout.LayoutParams(0, -1, 1f)
            )
        }
    }

    private fun showPage(page: String) {
        currentPage = page
        buildBottomNavigation()
        pageContainer.removeAllViews()

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(content)
        pageContainer.addView(scroll)

        when (page) {
            "home" -> buildHomePage(content)
            "transactions" -> buildTransactionsPage(content)
            "accounts" -> buildAccountsPage(content)
            "reports" -> buildReportsPage(content)
        }
        refresh()
    }

    private fun buildHomePage(content: LinearLayout) {
        content.addView(label("نمای کلی مالی", 21, GREEN, true))
        summary = label("", 16, DARK, true).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(GREEN)
            setTextColor(Color.WHITE)
        }
        content.addView(summary, matchWrap())

        content.addView(label("دسترسی سریع", 18, GREEN, true))
        val addButton = Button(this).apply {
            text = "＋  ثبت تراکنش جدید"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(GREEN)
            setOnClickListener { showPage("transactions") }
        }
        content.addView(addButton, matchWrap())

        content.addView(label("آخرین تراکنش‌ها", 18, GREEN, true))
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(listBox, matchWrap())
    }

    private fun buildTransactionsPage(content: LinearLayout) {
        content.addView(label("ثبت تراکنش و کالا", 21, GREEN, true))
        content.addView(label("نوع ثبت", 14, DARK, true))

        val types = listOf("واریزی", "برداشت", "آورده", "بدهکاری", "بستانکاری", "کالا")
        val type = Spinner(this)
        type.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        content.addView(type, matchWrap())

        val amount = EditText(this).apply {
            hint = "مبلغ به تومان"
            inputType = 2
            textSize = 16f
            setSingleLine(true)
        }
        content.addView(amount, matchWrap())
        val amountWords = label("", 13, GREEN).apply { setPadding(dp(4), 0, dp(4), dp(8)) }
        content.addView(amountWords, matchWrap())
        attachWords(amount, amountWords)

        val category = EditText(this).apply {
            hint = "موضوع / نام جنس"
            textSize = 16f
            setSingleLine(true)
        }
        content.addView(category, matchWrap())

        val productFields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(0xFFECE7D9.toInt())
        }
        content.addView(productFields, matchWrap())
        productFields.addView(label("مشخصات کالا (برای ثبت کالا)", 15, GREEN, true))

        val quantity = EditText(this).apply {
            hint = "مقدار (مثلاً 2.5)"
            inputType = 8194
            setSingleLine(true)
        }
        productFields.addView(quantity, matchWrap())
        val unit = Spinner(this)
        unit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("عدد", "کیلوگرم"))
        productFields.addView(unit, matchWrap())

        val purchasePrice = EditText(this).apply {
            hint = "قیمت خرید هر واحد (تومان)"
            inputType = 2
            setSingleLine(true)
        }
        productFields.addView(purchasePrice, matchWrap())
        val purchaseWords = label("", 13, GREEN).apply { setPadding(dp(4), 0, dp(4), dp(8)) }
        productFields.addView(purchaseWords, matchWrap())
        attachWords(purchasePrice, purchaseWords)

        val salePrice = EditText(this).apply {
            hint = "قیمت فروش هر واحد (تومان)"
            inputType = 2
            setSingleLine(true)
        }
        productFields.addView(salePrice, matchWrap())
        val saleWords = label("", 13, GREEN).apply { setPadding(dp(4), 0, dp(4), dp(8)) }
        productFields.addView(saleWords, matchWrap())
        attachWords(salePrice, saleWords)

        productFields.visibility = View.GONE
        type.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isProduct = types[position] == "کالا"
                productFields.visibility = if (isProduct) View.VISIBLE else View.GONE
                amount.visibility = if (isProduct) View.GONE else View.VISIBLE
                amountWords.visibility = if (isProduct) View.GONE else View.VISIBLE
                category.hint = if (isProduct) "نام جنس" else "موضوع / دسته"
            }
        }

        val note = EditText(this).apply {
            hint = "توضیحات"
            textSize = 16f
            minLines = 2
            gravity = Gravity.TOP or Gravity.RIGHT
        }
        content.addView(note, matchWrap())

        val dateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dateText = TextView(this).apply {
            text = "تاریخ: ${chosenDate.format()}"
            textSize = 15f
            setTextColor(DARK)
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        val dateButton = Button(this).apply {
            text = "انتخاب تاریخ"
            setTextColor(Color.WHITE)
            setBackgroundColor(GREEN)
            setOnClickListener {
                showJalaliPicker { selected -> chosenDate = selected; dateText.text = "تاریخ: ${selected.format()}" }
            }
        }
        dateRow.addView(dateText, LinearLayout.LayoutParams(0, dp(48), 1f))
        dateRow.addView(dateButton)
        content.addView(dateRow)

        val addButton = Button(this).apply {
            text = "ثبت اطلاعات"
            textSize = 17f
            setTextColor(Color.WHITE)
            setBackgroundColor(GREEN)
            setOnClickListener {
                val selectedKind = types[type.selectedItemPosition]
                val rawAmount = amount.text.toString().trim().toEnglishDigits().replace(",", "").replace("٬", "")
                val value = rawAmount.toLongOrNull()
                val q = quantity.text.toString().trim().toEnglishDigits().toDoubleOrNull() ?: 0.0
                val buy = purchasePrice.text.toString().trim().toEnglishDigits().replace(",", "").toLongOrNull() ?: 0L
                val sell = salePrice.text.toString().trim().toEnglishDigits().replace(",", "").toLongOrNull() ?: 0L
                val isProduct = selectedKind == "کالا"

                if (!isProduct && (value == null || value <= 0)) {
                    Toast.makeText(this@MainActivity, "مبلغ معتبر وارد کن", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (isProduct && (category.text.toString().trim().isEmpty() || q <= 0 || buy <= 0 || sell <= 0)) {
                    Toast.makeText(this@MainActivity, "نام جنس، مقدار و قیمت خرید و فروش را کامل وارد کن", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                txs.add(0, Tx(
                    System.currentTimeMillis(), selectedKind, if (isProduct) 0L else value!!,
                    category.text.toString().trim(), note.text.toString().trim(), chosenDate.format(),
                    if (isProduct) unit.selectedItem.toString() else "", if (isProduct) q else 0.0,
                    if (isProduct) buy else 0L, if (isProduct) sell else 0L
                ))
                saveData()
                amount.setText(""); category.setText(""); note.setText(""); quantity.setText(""); purchasePrice.setText(""); salePrice.setText("")
                chosenDate = Jalali.today()
                dateText.text = "تاریخ: ${chosenDate.format()}"
                refresh()
                Toast.makeText(this@MainActivity, "ثبت شد", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(addButton, matchWrap())

        content.addView(label("فهرست تراکنش‌ها و کالاها", 19, GREEN, true))
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listBox, matchWrap())
    }

    private fun buildAccountsPage(content: LinearLayout) {
        content.addView(label("حساب‌های باز", 21, GREEN, true))
        summary = label("", 16, DARK, true).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(GREEN)
            setTextColor(Color.WHITE)
        }
        content.addView(summary, matchWrap())

        content.addView(label("بدهکاری و بستانکاری", 18, GREEN, true))
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(listBox, matchWrap())
    }

    private fun buildReportsPage(content: LinearLayout) {
        content.addView(label("گزارش‌های مالی", 21, GREEN, true))
        summary = label("", 16, DARK, true).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(GREEN)
            setTextColor(Color.WHITE)
        }
        content.addView(summary, matchWrap())

        content.addView(label("گزارش این ماه", 18, GREEN, true))
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(listBox, matchWrap())
    }

    private fun updateClock() {
        if (!::headerDate.isInitialized) return

        val clock = SimpleDateFormat(
            "HH:mm:ss",
            Locale("fa", "IR")
        ).format(Date())

        headerDate.text = "${Jalali.today().format()}   |   $clock"
    }

    private fun refresh() {
        val income = txs.filter { it.kind == "واریزی" }.sumOf { it.amount }
        val expense = txs.filter { it.kind == "برداشت" }.sumOf { it.amount }
        val debt = txs.filter { it.kind == "بدهکاری" }.sumOf { it.amount }
        val credit = txs.filter { it.kind == "بستانکاری" }.sumOf { it.amount }
        val capital = txs.filter { it.kind == "آورده" }.sumOf { it.amount }
        val totalBalance = income + capital + debt - expense - credit

        if (!::summary.isInitialized || !::listBox.isInitialized) return

        val today = Jalali.today()
        val monthPrefix = String.format(Locale.US, "%04d/%02d/", today.year, today.month)
        val monthlyTxs = txs.filter { it.date.startsWith(monthPrefix) }
        val monthlyIncome = monthlyTxs.filter { it.kind == "واریزی" || it.kind == "آورده" }.sumOf { it.amount }
        val monthlyExpense = monthlyTxs.filter { it.kind == "برداشت" }.sumOf { it.amount }
        val monthlyDebt = monthlyTxs.filter { it.kind == "بدهکاری" }.sumOf { it.amount }
        val monthlyCredit = monthlyTxs.filter { it.kind == "بستانکاری" }.sumOf { it.amount }

        summary.text = when (currentPage) {
            "accounts" ->
                "طلب از دیگران: ${money(debt)} تومان\n" +
                "بدهی به دیگران: ${money(credit)} تومان\n" +
                "اثر خالص حساب‌ها: ${money(debt - credit)} تومان\n" +
                "مانده کل با طلب و بدهی: ${money(totalBalance)} تومان"
            "reports" ->
                "واریزی و آورده این ماه: ${money(monthlyIncome)} تومان\n" +
                "برداشت این ماه: ${money(monthlyExpense)} تومان\n" +
                "مانده این ماه با حساب‌ها: ${money(monthlyIncome + monthlyDebt - monthlyExpense - monthlyCredit)} تومان\n" +
                "تعداد تراکنش‌های ماه: ${monthlyTxs.size}"
            "transactions" ->
                "تعداد تراکنش‌ها: ${txs.size}\n" +
                "مانده کل: ${money(totalBalance)} تومان"
            else ->
                "کل واریزی: ${money(income)} تومان\n" +
                "آورده: ${money(capital)} تومان\n" +
                "کل برداشت: ${money(expense)} تومان\n" +
                "طلب: ${money(debt)} تومان\n" +
                "بدهی: ${money(credit)} تومان\n" +
                "مانده کل: ${money(totalBalance)} تومان"
        }

        listBox.removeAllViews()

        val visibleTxs = when (currentPage) {
            "home" -> txs.take(5)
            "accounts" -> txs.filter {
                it.kind == "بدهکاری" || it.kind == "بستانکاری"
            }
            "reports" -> monthlyTxs
            else -> txs
        }

        if (visibleTxs.isEmpty()) {
            val emptyMessage = when (currentPage) {
                "accounts" -> "هنوز بدهکاری یا بستانکاری ثبت نشده است."
                "reports" -> "برای این ماه تراکنشی ثبت نشده است."
                else -> "هنوز تراکنشی ثبت نشده است."
            }
            listBox.addView(label(emptyMessage, 14, Color.GRAY))
            return
        }

        visibleTxs.forEach { transaction ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.WHITE)
            }

            val titleText = if (transaction.kind == "کالا") {
                "کالا: ${transaction.category}"
            } else {
                "${transaction.kind} | ${money(transaction.amount)} تومان"
            }
            card.addView(label(titleText, 16, GREEN, true))
            if (transaction.kind == "کالا") {
                card.addView(label(
                    "مقدار: ${transaction.quantity} ${transaction.unit}\n" +
                    "خرید هر واحد: ${money(transaction.purchasePrice)} تومان\n" +
                    "فروش هر واحد: ${money(transaction.salePrice)} تومان",
                    14, DARK
                ))
            }

            card.addView(
                label(
                    "${transaction.date}  •  ${transaction.category}",
                    14,
                    DARK
                )
            )

            if (transaction.note.isNotBlank()) {
                card.addView(label(transaction.note, 14, Color.GRAY))
            }

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val editButton = Button(this).apply {
                text = "ویرایش"
                setOnClickListener { editTx(transaction) }
            }

            val deleteButton = Button(this).apply {
                text = "حذف"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("این تراکنش حذف شود؟")
                        .setPositiveButton("حذف") { _, _ ->
                            txs.removeAll { it.id == transaction.id }
                            saveData()
                            refresh()
                        }
                        .setNegativeButton("انصراف", null)
                        .show()
                }
            }

            actions.addView(editButton)
            actions.addView(deleteButton)
            card.addView(actions)
            listBox.addView(card, matchWrap())

            val divider = View(this).apply {
                setBackgroundColor(0xFFE5E1D8.toInt())
            }
            listBox.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }

    private fun editTx(transaction: Tx) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        val amount = EditText(this).apply {
            setText(transaction.amount.toString())
            inputType = 2
            hint = "مبلغ"
        }

        val category = EditText(this).apply {
            setText(transaction.category)
            hint = "موضوع"
        }

        val note = EditText(this).apply {
            setText(transaction.note)
            hint = "توضیحات"
        }

        val date = EditText(this).apply {
            setText(transaction.date)
            hint = "تاریخ شمسی: 1405/01/01"
        }

        layout.addView(amount)
        layout.addView(category)
        layout.addView(note)
        layout.addView(date)

        AlertDialog.Builder(this)
            .setTitle("ویرایش تراکنش")
            .setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                val value = amount.text.toString().toEnglishDigits().replace(",", "").toLongOrNull()

                if (value != null && value > 0) {
                    val index = txs.indexOfFirst {
                        it.id == transaction.id
                    }

                    if (index >= 0) {
                        txs[index] = transaction.copy(
                            amount = value,
                            category = category.text.toString(),
                            note = note.text.toString(),
                            date = date.text.toString()
                        )
                    }

                    saveData()
                    refresh()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showJalaliPicker(
        onPick: (Jalali.Date) -> Unit
    ) {
        var year = chosenDate.year
        var month = chosenDate.month

        val dialog = Dialog(this)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(CREAM)
        }

        val title = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(GREEN)
            setTypeface(null, Typeface.BOLD)
        }

        val daysBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun drawCalendar() {
            title.text = "${Jalali.monthName(month)} $year"
            daysBox.removeAllViews()

            val nav = LinearLayout(this).apply {
                gravity = Gravity.CENTER
            }

            val previous = Button(this).apply {
                text = "‹"
                setOnClickListener {
                    if (month == 1) {
                        month = 12
                        year--
                    } else {
                        month--
                    }
                    drawCalendar()
                }
            }

            val next = Button(this).apply {
                text = "›"
                setOnClickListener {
                    if (month == 12) {
                        month = 1
                        year++
                    } else {
                        month++
                    }
                    drawCalendar()
                }
            }

            nav.addView(previous)
            nav.addView(title)
            nav.addView(next)

            if (box.childCount == 0) {
                box.addView(nav)
            }

            val weekdays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
            val head = LinearLayout(this)

            weekdays.forEach { weekday ->
                head.addView(
                    TextView(this).apply {
                        text = weekday
                        gravity = Gravity.CENTER
                        setTextColor(GREEN)
                    },
                    LinearLayout.LayoutParams(0, dp(34), 1f)
                )
            }

            daysBox.addView(head)

            val first = Jalali.toGregorian(year, month, 1)
            val offset = first.get(Calendar.DAY_OF_WEEK) % 7

            val count = when {
                month <= 6 -> 31
                month <= 11 -> 30
                Jalali.isLeap(year) -> 30
                else -> 29
            }

            var day = 1

            for (rowIndex in 0 until 6) {
                val row = LinearLayout(this)

                for (column in 0 until 7) {
                    val cell = TextView(this).apply {
                        gravity = Gravity.CENTER
                        textSize = 15f
                        setTextColor(DARK)

                        if ((rowIndex * 7 + column) >= offset &&
                            day <= count
                        ) {
                            val selectedDay = day++
                            text = selectedDay.toString()

                            setOnClickListener {
                                val selected = Jalali.Date(
                                    year,
                                    month,
                                    selectedDay
                                )

                                dialog.dismiss()
                                onPick(selected)
                            }
                        }
                    }

                    row.addView(
                        cell,
                        LinearLayout.LayoutParams(0, dp(40), 1f)
                    )
                }

                daysBox.addView(row)

                if (day > count) break
            }
        }

        box.addView(daysBox)
        drawCalendar()

        dialog.setContentView(box)
        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            -2
        )
    }

    private fun saveData() {
        val array = JSONArray()

        txs.forEach { transaction ->
            array.put(
                JSONObject()
                    .put("id", transaction.id)
                    .put("kind", transaction.kind)
                    .put("amount", transaction.amount)
                    .put("category", transaction.category)
                    .put("note", transaction.note)
                    .put("date", transaction.date)
                    .put("unit", transaction.unit)
                    .put("quantity", transaction.quantity)
                    .put("purchasePrice", transaction.purchasePrice)
                    .put("salePrice", transaction.salePrice)
            )
        }

        prefs.edit()
            .putString("transactions", array.toString())
            .apply()
    }

    private fun loadData() {
        try {
            val array = JSONArray(
                prefs.getString("transactions", "[]")
            )

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                txs.add(
                    Tx(
                        obj.getLong("id"),
                        obj.getString("kind"),
                        obj.getLong("amount"),
                        obj.optString("category"),
                        obj.optString("note"),
                        obj.optString("date"),
                        obj.optString("unit"),
                        obj.optDouble("quantity", 0.0),
                        obj.optLong("purchasePrice", 0L),
                        obj.optLong("salePrice", 0L)
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun attachWords(input: EditText, output: TextView) {
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val number = s?.toString()?.toEnglishDigits()?.replace(",", "")?.replace("٬", "")?.toLongOrNull()
                output.text = if (number != null && number > 0) "به حروف: ${numberToPersianWords(number)} تومان" else ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun numberToPersianWords(number: Long): String {
        if (number == 0L) return "صفر"
        val units = arrayOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
        val teens = arrayOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
        val tens = arrayOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
        val hundreds = arrayOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
        fun under1000(n: Int): String {
            val parts = mutableListOf<String>()
            if (n >= 100) parts.add(hundreds[n / 100])
            val r = n % 100
            if (r in 10..19) parts.add(teens[r - 10]) else {
                if (r >= 20) parts.add(tens[r / 10])
                if (r % 10 > 0) parts.add(units[r % 10])
            }
            return parts.joinToString(" و ")
        }
        val scales = arrayOf("", "هزار", "میلیون", "میلیارد", "تریلیون", "کوادریلیون")
        var value = number
        var scale = 0
        val parts = mutableListOf<String>()
        while (value > 0 && scale < scales.size) {
            val chunk = (value % 1000).toInt()
            if (chunk > 0) {
                val word = under1000(chunk)
                parts.add(if (scales[scale].isEmpty()) word else "$word ${scales[scale]}")
            }
            value /= 1000
            scale++
        }
        return parts.asReversed().joinToString(" و ")
    }

    private fun String.toEnglishDigits(): String {
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        return buildString {
            this@toEnglishDigits.forEach { ch ->
                val p = persian.indexOf(ch)
                val a = arabic.indexOf(ch)
                append(when { p >= 0 -> ('0'.code + p).toChar(); a >= 0 -> ('0'.code + a).toChar(); else -> ch })
            }
        }
    }

    private fun label(
        text: String,
        size: Int,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)

        if (bold) {
            setTypeface(null, Typeface.BOLD)
        }

        setPadding(0, dp(8), 0, dp(8))
    }

    private fun matchWrap() =
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(5)
            bottomMargin = dp(5)
        }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

    private fun money(value: Long) =
        String.format(Locale("fa", "IR"), "%,d", value)
}

object Jalali {

    data class Date(
        val year: Int,
        val month: Int,
        val day: Int
    ) {
        fun format() =
            String.format(
                Locale.US,
                "%04d/%02d/%02d",
                year,
                month,
                day
            )
    }

    fun today(): Date {
        val calendar = Calendar.getInstance()

        return fromGregorian(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun monthName(month: Int) =
        listOf(
            "",
            "فروردین",
            "اردیبهشت",
            "خرداد",
            "تیر",
            "مرداد",
            "شهریور",
            "مهر",
            "آبان",
            "آذر",
            "دی",
            "بهمن",
            "اسفند"
        )[month]

    fun isLeap(year: Int): Boolean {
        val gregorian = toGregorian(year, 12, 29)

        return gregorian.get(Calendar.MONTH) == Calendar.MARCH &&
            gregorian.get(Calendar.DAY_OF_MONTH) == 20
    }

    fun toGregorian(
        year: Int,
        month: Int,
        day: Int
    ): Calendar {
        val jdn = jalaliToJdn(year, month, day)
        val gregorian = jdnToGregorian(jdn)

        return Calendar.getInstance().apply {
            set(
                gregorian[0],
                gregorian[1] - 1,
                gregorian[2],
                12,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun fromGregorian(
        gy: Int,
        gm: Int,
        gd: Int
    ): Date {
        val jdn = gregorianToJdn(gy, gm, gd)
        var jy = gy - 621

        while (jalaliToJdn(jy + 1, 1, 1) <= jdn) {
            jy++
        }

        while (jalaliToJdn(jy, 1, 1) > jdn) {
            jy--
        }

        val dayOfYear = jdn - jalaliToJdn(jy, 1, 1)

        val jm = if (dayOfYear < 186) {
            dayOfYear / 31 + 1
        } else {
            (dayOfYear - 186) / 30 + 7
        }

        val jd = if (dayOfYear < 186) {
            dayOfYear % 31 + 1
        } else {
            (dayOfYear - 186) % 30 + 1
        }

        return Date(jy, jm, jd)
    }

    private fun jalaliToJdn(
        jy: Int,
        jm: Int,
        jd: Int
    ): Int {
        val gy = jy + 621
        val march = 20 + jalaliLeap(jy)

        val days = if (jm <= 7) {
            (jm - 1) * 31
        } else {
            186 + (jm - 7) * 30
        }

        return gregorianToJdn(gy, 3, march) + days + jd - 1
    }

    private fun jalaliLeap(jy: Int): Int {
        val breaks = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818,
            1111, 1181, 1210, 1635, 2060, 2097,
            2192, 2262, 2324, 2394, 2456, 3178
        )

        var jp = breaks[0]
        var jump = 0

        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp

            if (jy < jm) break
            jp = jm
        }

        var n = jy - jp

        if (jump - n < 6) {
            n = n - jump + ((jump + 4) / 33) * 33
        }

        var leap = (((n + 1) % 33) - 1) % 4

        if (leap == -1) {
            leap = 4
        }

        return if (leap == 0) 1 else 0
    }

    private fun gregorianToJdn(
        year: Int,
        month: Int,
        day: Int
    ): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3

        return day +
            (153 * m + 2) / 5 +
            365 * y +
            y / 4 -
            y / 100 +
            y / 400 -
            32045
    }

    private fun jdnToGregorian(jdn: Int): IntArray {
        val a = jdn + 32044
        val b = (4 * a + 3) / 146097
        val c = a - (146097 * b) / 4
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153

        return intArrayOf(
            100 * b + d - 4800 + m / 10,
            m + 3 - 12 * (m / 10),
            e - (153 * m + 2) / 5 + 1
        )
    }
}
