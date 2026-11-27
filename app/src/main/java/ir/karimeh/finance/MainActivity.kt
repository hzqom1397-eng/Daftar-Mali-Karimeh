package ir.karimeh.finance

import android.app.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

data class InventoryProduct(
    val id: Long,
    var name: String,
    var unit: String,
    var totalPurchased: Double,
    var totalSold: Double,
    var stock: Double,
    var totalPurchaseCost: Long,
    var totalSalesRevenue: Long,
    var costOfGoodsSold: Long,
    var salePrice: Long,
    var purchasePrice: Long = 0L
)

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
    private val products = mutableListOf<InventoryProduct>()
    private val customers = mutableListOf<String>()
    private var reportRange = "ماهانه"
    private var reportRangeSpinner: Spinner? = null
    private lateinit var headerDate: TextView
    private lateinit var summary: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var customersBox: LinearLayout
    private lateinit var pageContainer: FrameLayout
    private lateinit var bottomNav: LinearLayout

    private var currentPage = "home"

    private fun appPrimary(): Int = prefs.getInt("ui_primary", GREEN)
    private fun appBackground(): Int = prefs.getInt("ui_background", CREAM)
    private fun appFontScale(): Float = prefs.getFloat("ui_font_scale", 1.0f)
    private fun appTypeface(): Typeface = when (prefs.getString("ui_font", "پیش‌فرض")) {
        "رسمی" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        "ساده" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        "کلاسیک" -> Typeface.create("serif", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }

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

        window.statusBarColor = appPrimary()
        window.navigationBarColor = appPrimary()
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        loadData()
        loadInventory()
        loadCustomers()
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
            setBackgroundColor(appBackground())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(appPrimary())
        }

        header.addView(label("دفتر مالی کریمه", 23, Color.WHITE, true))
        headerDate = label("", 13, Color.WHITE)
        header.addView(headerDate)
        val goldStripe = View(this).apply { setBackgroundColor(GOLD) }
        header.addView(goldStripe, LinearLayout.LayoutParams(-1, dp(3)))
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        pageContainer = FrameLayout(this).apply {
            setBackgroundColor(appBackground())
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
            Triple("inventory", "لیست کالاها", "▣"),
            Triple("accounts", "حساب‌ها", "▤"),
            Triple("reports", "گزارش‌ها", "▥"),
            Triple("settings", "تنظیمات", "⚙")
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
                textSize = 18f * appFontScale()
                gravity = Gravity.CENTER
                setTextColor(if (currentPage == item.first) appPrimary() else Color.GRAY)
            }
            val title = TextView(this).apply {
                text = item.second
                textSize = 9.5f * appFontScale()
                gravity = Gravity.CENTER
                setTypeface(null, if (currentPage == item.first) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (currentPage == item.first) appPrimary() else Color.GRAY)
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
            "inventory" -> buildInventoryPage(content)
            "accounts" -> buildAccountsPage(content)
            "reports" -> buildReportsPage(content)
            "settings" -> buildSettingsPage(content)
        }
        refresh()
    }

    private fun buildHomePage(content: LinearLayout) {
        content.addView(label("نمای کلی فروشگاه", 21, appPrimary(), true))
        summary = label("", 1, DARK).apply { visibility = View.GONE }
        content.addView(summary)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listBox, matchWrap())
        val addButton = Button(this).apply {
            text = "＋  ثبت تراکنش جدید"
            textSize = 16f * appFontScale()
            setTextColor(Color.WHITE)
            setBackgroundColor(appPrimary())
            setOnClickListener { showPage("transactions") }
        }
        content.addView(addButton, matchWrap())
    }

    private fun addHomeCard(title: String, value: String, detail: String = "") {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0xFFE6E1D5.toInt())
            }
        }
        card.addView(label(title, 14, Color.GRAY, true))
        card.addView(label(value, 20, appPrimary(), true))
        if (detail.isNotBlank()) card.addView(label(detail, 13, DARK))
        listBox.addView(card, matchWrap())
    }

    private fun buildSettingsPage(content: LinearLayout) {
        content.addView(label("تنظیمات و ظاهر برنامه", 21, appPrimary(), true))
        content.addView(label("ظاهر برنامه را مطابق سلیقه خود تنظیم کنید. تغییرات ذخیره می‌شوند.", 14, DARK))

        fun settingButton(title: String, description: String, onClick: () -> Unit) {
            val button = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat(); setStroke(dp(1), 0xFFE6E1D5.toInt()) }
                isClickable = true; isFocusable = true; setOnClickListener { onClick() }
            }
            button.addView(label(title, 16, appPrimary(), true))
            button.addView(label(description, 13, Color.GRAY))
            content.addView(button, matchWrap())
        }

        settingButton("رنگ پس‌زمینه", "انتخاب زمینه کرم، سفید یا خاکستری روشن") {
            val names = arrayOf("کرم روشن", "سفید", "خاکستری روشن")
            val colors = intArrayOf(CREAM, Color.WHITE, 0xFFF0F2F3.toInt())
            AlertDialog.Builder(this).setTitle("رنگ پس‌زمینه").setItems(names) { _, which ->
                prefs.edit().putInt("ui_background", colors[which]).apply()
                buildUi(); showPage("settings")
            }.show()
        }
        settingButton("رنگ اصلی برنامه", "انتخاب رنگ سبز، آبی یا زرشکی") {
            val names = arrayOf("سبز زمردی", "آبی تیره", "زرشکی")
            val colors = intArrayOf(GREEN, 0xFF244A73.toInt(), 0xFF7A3046.toInt())
            AlertDialog.Builder(this).setTitle("رنگ اصلی").setItems(names) { _, which ->
                prefs.edit().putInt("ui_primary", colors[which]).apply()
                buildUi(); showPage("settings")
            }.show()
        }
        settingButton("اندازه نوشته‌ها", "کوچک، معمولی، بزرگ یا خیلی بزرگ") {
            val names = arrayOf("کوچک", "معمولی", "بزرگ", "خیلی بزرگ")
            val scales = floatArrayOf(0.90f, 1.0f, 1.12f, 1.25f)
            AlertDialog.Builder(this).setTitle("اندازه فونت").setItems(names) { _, which ->
                prefs.edit().putFloat("ui_font_scale", scales[which]).apply()
                buildUi(); showPage("settings")
            }.show()
        }
        settingButton("نوع فونت", "پیش‌فرض، رسمی، ساده یا کلاسیک") {
            val names = arrayOf("پیش‌فرض", "رسمی", "ساده", "کلاسیک")
            AlertDialog.Builder(this).setTitle("انتخاب فونت").setItems(names) { _, which ->
                prefs.edit().putString("ui_font", names[which]).apply()
                buildUi(); showPage("settings")
            }.show()
        }
        settingButton("بازگشت به ظاهر اولیه", "بازگرداندن رنگ‌ها و اندازه نوشته به حالت اولیه") {
            prefs.edit().remove("ui_primary").remove("ui_background").remove("ui_font_scale").remove("ui_font").apply()
            window.statusBarColor = GREEN; window.navigationBarColor = GREEN
            buildUi(); showPage("settings")
        }
    }

    private fun buildTransactionsPage(content: LinearLayout) {
        content.addView(label("ثبت تراکنش و کالا", 21, appPrimary(), true))
        content.addView(label("نوع ثبت", 14, DARK, true))

        val types = listOf("واریزی", "برداشت", "آورده", "بدهکاری", "بستانکاری", "کالا")
        val typeLabels = listOf("عملیات پولی  •  واریزی", "عملیات پولی  •  برداشت", "سرمایه  •  آورده", "حساب نسیه  •  بدهکاری", "حساب نسیه  •  بستانکاری", "مدیریت کالا  •  کالا")
        content.addView(label("دسته‌بندی ثبت", 14, DARK, true))
        val type = Spinner(this)
        type.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeLabels)
        content.addView(type, matchWrap())

        val amount = EditText(this).apply {
            hint = "مبلغ به تومان"
            inputType = 2
            textSize = 16f * appFontScale()
            setSingleLine(true)
        }
        content.addView(amount, matchWrap())
        val amountWords = label("", 13, appPrimary()).apply { setPadding(dp(4), 0, dp(4), dp(8)) }
        content.addView(amountWords, matchWrap())
        attachWords(amount, amountWords)

        val category = EditText(this).apply {
            hint = "موضوع / نام جنس"
            textSize = 16f * appFontScale()
            setSingleLine(true)
        }
        content.addView(category, matchWrap())

        val productFields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(0xFFECE7D9.toInt())
        }
        content.addView(productFields, matchWrap())
        productFields.addView(label("مشخصات کالا (برای ثبت کالا)", 15, appPrimary(), true))

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
        val purchaseWords = label("", 13, appPrimary()).apply { setPadding(dp(4), 0, dp(4), dp(8)) }
        productFields.addView(purchaseWords, matchWrap())
        attachWords(purchasePrice, purchaseWords)

        val salePrice = EditText(this).apply {
            hint = "قیمت فروش هر واحد (تومان)"
            inputType = 2
            setSingleLine(true)
        }
        productFields.addView(salePrice, matchWrap())
        val saleWords = label("", 13, appPrimary()).apply { setPadding(dp(4), 0, dp(4), dp(8)) }
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
            textSize = 16f * appFontScale()
            minLines = 2
            gravity = Gravity.TOP or Gravity.RIGHT
        }
        content.addView(note, matchWrap())

        val dateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dateText = TextView(this).apply {
            text = "تاریخ: ${chosenDate.format()}"
            textSize = 15f * appFontScale()
            setTextColor(DARK)
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        val dateButton = Button(this).apply {
            text = "انتخاب تاریخ"
            setTextColor(Color.WHITE)
            setBackgroundColor(appPrimary())
            setOnClickListener {
                showJalaliPicker { selected -> chosenDate = selected; dateText.text = "تاریخ: ${selected.format()}" }
            }
        }
        dateRow.addView(dateText, LinearLayout.LayoutParams(0, dp(48), 1f))
        dateRow.addView(dateButton)
        content.addView(dateRow)

        val addButton = Button(this).apply {
            text = "ثبت اطلاعات"
            textSize = 17f * appFontScale()
            setTextColor(Color.WHITE)
            setBackgroundColor(appPrimary())
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

        content.addView(label("فهرست تراکنش‌ها و کالاها", 19, appPrimary(), true))
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listBox, matchWrap())
    }

    private fun buildInventoryPage(content: LinearLayout) {
        content.addView(label("لیست کالاها", 21, appPrimary(), true))
        val inventorySummary = label("", 15, DARK, true).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply { setColor(0xFFECE7D9.toInt()); cornerRadius = dp(16).toFloat() }
        }
        content.addView(inventorySummary, matchWrap())
        val inventoryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(inventoryList, matchWrap())

        fun renderInventory() {
            val totalStockValue = products.sumOf { p ->
                (p.totalPurchaseCost - p.costOfGoodsSold).coerceAtLeast(0L)
            }
            val totalCost = products.sumOf { it.totalPurchaseCost }
            val totalSales = products.sumOf { it.totalSalesRevenue }
            val totalProfit = products.sumOf { it.totalSalesRevenue - it.costOfGoodsSold }
            inventorySummary.text = "تعداد کالاها: ${products.size}\nمجموع خریدها: ${money(totalCost)} تومان\nارزش موجودی فعلی: ${money(totalStockValue)} تومان\nفروش ثبت‌شده: ${money(totalSales)} تومان\nسود ناخالص فروش: ${money(totalProfit)} تومان"
            inventoryList.removeAllViews()
            if (products.isEmpty()) {
                inventoryList.addView(label("هنوز کالایی ثبت نشده است. برای شروع، افزودن کالا را بزن.", 14, Color.GRAY))
                return
            }
            products.forEach { product ->
                val avgCost = if (product.totalPurchased > 0) (product.totalPurchaseCost / product.totalPurchased).toLong() else 0L
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(14).toFloat() }
                }
                card.addView(label(product.name, 17, appPrimary(), true))
                card.addView(label("واحد: ${product.unit}\nمجموع خرید: ${qty(product.totalPurchased)} ${product.unit}\nموجودی فعلی: ${qty(product.stock)} ${product.unit}\nقیمت خرید فعلی: ${money(product.purchasePrice.takeIf { it > 0 } ?: avgCost)} تومان\nبه حروف: ${numberToPersianWords(product.purchasePrice.takeIf { it > 0 } ?: avgCost)} تومان\nقیمت خرید میانگین: ${money(avgCost)} تومان\nقیمت فروش: ${money(product.salePrice)} تومان\nبه حروف: ${numberToPersianWords(product.salePrice)} تومان\nمجموع هزینه خرید: ${money(product.totalPurchaseCost)} تومان\nبه حروف: ${numberToPersianWords(product.totalPurchaseCost)} تومان\nفروش کل: ${money(product.totalSalesRevenue)} تومان\nسود ناخالص: ${money(product.totalSalesRevenue - product.costOfGoodsSold)} تومان", 14, DARK))
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val buy = Button(this).apply { text = "خرید مجدد"; setOnClickListener { inventoryMovement(product, true) { renderInventory() } } }
                val sell = Button(this).apply { text = "ثبت فروش"; setOnClickListener { inventoryMovement(product, false) { renderInventory() } } }
                val edit = Button(this).apply { text = "ویرایش قیمت"; setOnClickListener { editInventoryProduct(product) { renderInventory() } } }
                row.addView(buy, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(sell, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(edit, LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(row)
                inventoryList.addView(card, matchWrap())
            }
        }

        val add = Button(this).apply {
            text = "＋ افزودن کالا / ثبت خرید اولیه"
            setTextColor(Color.WHITE); setBackgroundColor(appPrimary())
            setOnClickListener { addInventoryProduct { renderInventory() } }
        }
        content.addView(add, matchWrap())
        renderInventory()
    }

    private fun addInventoryProduct(done: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        fun field(hintText: String, numeric: Boolean = false): EditText = EditText(this).apply {
            hint = hintText; textSize = 16f; if (numeric) inputType = 8194; setSingleLine(true)
        }
        val name = field("نام کالا / نوع جنس")
        val quantity = field("مقدار خرید اولیه", true)
        val unit = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("کیلوگرم", "عدد / تعداد")) }
        val buy = field("قیمت خرید هر واحد (تومان)", true)
        val buyWords = label("", 12, appPrimary())
        attachWords(buy, buyWords)
        val sell = field("قیمت فروش هر واحد (تومان)", true)
        val sellWords = label("", 12, appPrimary())
        attachWords(sell, sellWords)
        val totalWords = label("", 12, appPrimary())
        val payment = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("نقدی", "نسیه")) }
        fun updateTotalWords() {
            val q = quantity.text.toString().toEnglishDigits().toDoubleOrNull() ?: 0.0
            val b = buy.text.toString().toEnglishDigits().replace(",", "").replace("٬", "").toLongOrNull() ?: 0L
            val total = (q * b).toLong()
            totalWords.text = if (total > 0) "هزینه کل خرید: ${money(total)} تومان\nبه حروف: ${numberToPersianWords(total)} تومان" else ""
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateTotalWords() }
            override fun afterTextChanged(s: Editable?) {}
        }
        quantity.addTextChangedListener(watcher); buy.addTextChangedListener(watcher)
        box.addView(name); box.addView(quantity); box.addView(unit); box.addView(buy); box.addView(buyWords)
        box.addView(sell); box.addView(sellWords); box.addView(totalWords)
        box.addView(label("روش خرید اولیه", 14, DARK, true)); box.addView(payment)
        AlertDialog.Builder(this).setTitle("افزودن کالا").setView(box)
            .setPositiveButton("ثبت کالا") { _, _ ->
                val n = name.text.toString().trim()
                val q = quantity.text.toString().toEnglishDigits().toDoubleOrNull() ?: 0.0
                val b = buy.text.toString().toEnglishDigits().replace(",", "").replace("٬", "").toLongOrNull() ?: 0L
                val v = sell.text.toString().toEnglishDigits().replace(",", "").replace("٬", "").toLongOrNull() ?: 0L
                if (n.isBlank() || q <= 0 || b <= 0 || v <= 0) {
                    Toast.makeText(this, "نام، مقدار و قیمت‌ها را درست وارد کن", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                val amount = (q * b).toLong()
                val product = InventoryProduct(System.currentTimeMillis(), n, unit.selectedItem.toString(), q, 0.0, q, amount, 0L, 0L, v, b)
                products.add(product)
                val isCredit = payment.selectedItemPosition == 1
                addInventoryCashTx(if (isCredit) "بستانکاری" else "برداشت", amount,
                    if (isCredit) "بدهی به فروشنده بابت خرید: $n" else "خرید موجودی: $n", "INV|BUY|${product.id}")
                saveInventory(); saveData(); done()
                Toast.makeText(this, if (isCredit) "کالا ثبت شد؛ مبلغ خرید به بدهی فروشنده اضافه شد" else "کالا ثبت شد و مبلغ خرید از مانده نقدی کسر شد", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("انصراف", null).show()
    }

    private fun inventoryMovement(product: InventoryProduct, purchase: Boolean, done: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val quantity = EditText(this).apply { hint = "مقدار ${product.unit}"; inputType = 8194; setSingleLine(true) }
        val price = EditText(this).apply { hint = if (purchase) "قیمت خرید هر واحد" else "قیمت فروش هر واحد"; inputType = 2; setSingleLine(true); setText((if (purchase) (product.purchasePrice.takeIf { it > 0 } ?: (product.totalPurchaseCost / product.totalPurchased.coerceAtLeast(1.0)).toLong()) else product.salePrice).toString()) }
        val priceWords = label("", 12, appPrimary()); attachWords(price, priceWords)
        val payment = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("نقدی", "نسیه")) }
        val salePriceMode = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("حفظ قیمت فروش قبلی", "تعیین قیمت فروش جدید")) }
        val newSalePrice = EditText(this).apply { hint = "قیمت فروش جدید هر واحد (تومان)"; inputType = 2; setSingleLine(true); setText(product.salePrice.toString()) }
        val newSaleWords = label("قیمت فعلی: ${money(product.salePrice)} تومان", 12, appPrimary()); attachWords(newSalePrice, newSaleWords)
        box.addView(quantity); box.addView(price); box.addView(priceWords)
        if (purchase) {
            box.addView(label("قیمت فروش پس از خرید مجدد", 14, DARK, true)); box.addView(salePriceMode)
            box.addView(newSalePrice); box.addView(newSaleWords)
            newSalePrice.visibility = View.GONE; newSaleWords.visibility = View.GONE
            salePriceMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    newSalePrice.visibility = if (position == 1) View.VISIBLE else View.GONE
                    newSaleWords.visibility = if (position == 1) View.VISIBLE else View.GONE
                }
            }
        }
        box.addView(label(if (purchase) "روش پرداخت خرید" else "نوع فروش", 14, DARK, true)); box.addView(payment)

        val customerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val customerSpinner = Spinner(this)
        val customerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, customers.toMutableList())
        customerSpinner.adapter = customerAdapter
        val addCustomer = Button(this).apply { text = "+ مشتری"; setOnClickListener {
            addCustomerDialog { added ->
                customerAdapter.clear(); customerAdapter.addAll(customers); customerAdapter.notifyDataSetChanged()
                val index = customers.indexOf(added); if (index >= 0) customerSpinner.setSelection(index)
            }
        } }
        customerRow.addView(customerSpinner, LinearLayout.LayoutParams(0, -2, 1f)); customerRow.addView(addCustomer)
        box.addView(customerRow)
        customerRow.visibility = View.GONE
        payment.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                customerRow.visibility = if (!purchase && position == 1) View.VISIBLE else View.GONE
            }
        }
        AlertDialog.Builder(this).setTitle(if (purchase) "خرید مجدد: ${product.name}" else "ثبت فروش: ${product.name}").setView(box)
            .setPositiveButton("ثبت") { _, _ ->
                val q = quantity.text.toString().toEnglishDigits().toDoubleOrNull() ?: 0.0
                val p = price.text.toString().toEnglishDigits().replace(",", "").replace("٬", "").toLongOrNull() ?: 0L
                if (q <= 0 || p <= 0) { Toast.makeText(this, "مقدار و قیمت معتبر وارد کن", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (!purchase && q > product.stock) { Toast.makeText(this, "موجودی کافی نیست؛ موجودی فعلی ${qty(product.stock)} است", Toast.LENGTH_LONG).show(); return@setPositiveButton }
                val isCredit = payment.selectedItemPosition == 1
                if (!purchase && isCredit && customers.isEmpty()) { Toast.makeText(this, "ابتدا یک مشتری ثبت کن", Toast.LENGTH_LONG).show(); return@setPositiveButton }
                val amount = (q * p).toLong()
                if (purchase) {
                    val selectedNewSalePrice = newSalePrice.text.toString().toEnglishDigits().replace(",", "").replace("٬", "").toLongOrNull() ?: 0L
                    if (salePriceMode.selectedItemPosition == 1 && selectedNewSalePrice <= 0L) {
                        Toast.makeText(this, "قیمت فروش جدید معتبر وارد کنید", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                    }
                    product.totalPurchased += q; product.stock += q; product.totalPurchaseCost += amount; product.purchasePrice = p
                    if (salePriceMode.selectedItemPosition == 1) product.salePrice = selectedNewSalePrice
                    addInventoryCashTx(if (isCredit) "بستانکاری" else "برداشت", amount,
                        if (isCredit) "بدهی به فروشنده بابت خرید: ${product.name}" else "خرید موجودی: ${product.name}", "INV|BUY|${product.id}")
                } else {
                    val averageCost = if (product.totalPurchased > 0) product.totalPurchaseCost / product.totalPurchased else 0.0
                    val cogs = (q * averageCost).toLong()
                    product.totalSold += q; product.stock -= q; product.totalSalesRevenue += amount; product.costOfGoodsSold += cogs; product.salePrice = p
                    val customer = if (isCredit) customers.getOrNull(customerSpinner.selectedItemPosition).orEmpty() else ""
                    addInventoryCashTx(if (isCredit) "بدهکاری" else "واریزی", amount,
                        if (isCredit) "طلب از مشتری: $customer | فروش ${product.name}" else "فروش کالا: ${product.name}",
                        if (isCredit) "INV|SELL|${product.id}|CREDIT|$customer" else "INV|SELL|${product.id}")
                }
                saveInventory(); saveData(); done(); refresh()
                Toast.makeText(this, when { purchase && isCredit -> "خرید ثبت شد و به بدهی فروشنده اضافه شد"; purchase -> "خرید ثبت شد و از مانده نقدی کسر شد"; isCredit -> "فروش نسیه ثبت شد؛ طلب مشتری افزایش یافت"; else -> "فروش ثبت شد و به مانده نقدی اضافه شد" }, Toast.LENGTH_SHORT).show()
            }.setNegativeButton("انصراف", null).show()
    }

    private fun settlementDialog(paySupplier: Boolean, done: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val party = if (paySupplier) EditText(this).apply { hint = "نام فروشنده (مثلاً فروشنده اصلی)"; setText("فروشنده اصلی") }
        else Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, customers.toList()) }
        val amount = EditText(this).apply { hint = "مبلغ پرداخت / دریافت به تومان"; inputType = 2; setSingleLine(true) }
        val words = label("", 12, appPrimary()); attachWords(amount, words)
        box.addView(label(if (paySupplier) "پرداخت به فروشنده" else "دریافت از مشتری", 15, appPrimary(), true))
        box.addView(party); box.addView(amount); box.addView(words)
        AlertDialog.Builder(this).setTitle(if (paySupplier) "تسویه بدهی فروشنده" else "دریافت طلب مشتری").setView(box)
            .setPositiveButton("ثبت تسویه") { _, _ ->
                val value = amount.text.toString().toEnglishDigits().replace(",", "").replace("٬", "").toLongOrNull() ?: 0L
                val person = if (paySupplier) (party as EditText).text.toString().trim() else (party as Spinner).selectedItem?.toString().orEmpty()
                if (value <= 0 || person.isBlank()) { Toast.makeText(this, "نام طرف حساب و مبلغ معتبر وارد کنید", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val outstanding = if (paySupplier) {
                    (txs.filter { it.kind == "بستانکاری" }.sumOf { it.amount } - txs.filter { it.kind == "پرداخت بدهی" }.sumOf { it.amount }).coerceAtLeast(0L)
                } else {
                    val customerOriginal = txs.filter { it.kind == "بدهکاری" && it.category.startsWith("طلب از مشتری: $person |") }.sumOf { it.amount }
                    val customerReceived = txs.filter { it.kind == "دریافت طلب" && it.category == "دریافت از مشتری: $person" }.sumOf { it.amount }
                    (customerOriginal - customerReceived).coerceAtLeast(0L)
                }
                if (value > outstanding) {
                    Toast.makeText(this, "مبلغ از مانده حساب بیشتر است. مانده: ${money(outstanding)} تومان", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val kind = if (paySupplier) "پرداخت بدهی" else "دریافت طلب"
                val category = if (paySupplier) "پرداخت به فروشنده: $person" else "دریافت از مشتری: $person"
                txs.add(0, Tx(System.currentTimeMillis(), kind, value, category, "تسویه حساب", Jalali.today().format()))
                saveData(); done()
                Toast.makeText(this, "تسویه ثبت شد", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("انصراف", null).show()
    }

    private fun addCustomerDialog(onAdded: (String) -> Unit = {}) {
        val input = EditText(this).apply { hint = "نام مشتری"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("ثبت مشتری جدید").setView(input)
            .setPositiveButton("ثبت") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) { Toast.makeText(this, "نام مشتری را وارد کن", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (!customers.contains(name)) customers.add(name)
                saveCustomers(); onAdded(name)
            }.setNegativeButton("انصراف", null).show()
    }

    private fun saveCustomers() {
        prefs.edit().putString("karimeh_customers", JSONArray(customers).toString()).apply()
    }

    private fun loadCustomers() {
        try {
            val array = JSONArray(prefs.getString("karimeh_customers", "[]"))
            for (i in 0 until array.length()) {
                val name = array.optString(i).trim()
                if (name.isNotBlank() && !customers.contains(name)) customers.add(name)
            }
        } catch (_: Exception) { }
    }

    private fun editInventoryProduct(product: InventoryProduct, done: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val name = EditText(this).apply { hint = "نام کالا"; setText(product.name) }
        val purchase = EditText(this).apply { hint = "قیمت خرید هر واحد"; inputType = 2; setText((product.purchasePrice.takeIf { it > 0 } ?: (product.totalPurchaseCost / product.totalPurchased.coerceAtLeast(1.0)).toLong()).toString()) }
        val purchaseWords = label("", 12, appPrimary()); attachWords(purchase, purchaseWords)
        val sale = EditText(this).apply { hint = "قیمت فروش هر واحد"; inputType = 2; setText(product.salePrice.toString()) }
        val saleWords = label("", 12, appPrimary()); attachWords(sale, saleWords)
        box.addView(name); box.addView(purchase); box.addView(purchaseWords); box.addView(sale); box.addView(saleWords)
        AlertDialog.Builder(this).setTitle("ویرایش مشخصات کالا").setView(box).setPositiveButton("ذخیره") { _, _ ->
            val newName = name.text.toString().trim()
            val newPurchase = purchase.text.toString().toEnglishDigits().replace(",", "").replace("٬", "").toLongOrNull() ?: 0L
            val newSale = sale.text.toString().toEnglishDigits().replace(",", "").replace("٬", "").toLongOrNull() ?: 0L
            if (newName.isNotBlank() && newPurchase > 0 && newSale > 0) {
                product.name = newName; product.purchasePrice = newPurchase; product.salePrice = newSale
                saveInventory(); done()
            } else Toast.makeText(this, "نام و هر دو قیمت باید معتبر باشند", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("انصراف", null).show()
    }

    private fun addInventoryCashTx(kind: String, amount: Long, category: String, marker: String) {
        txs.add(0, Tx(System.currentTimeMillis(), kind, amount, category, marker, Jalali.today().format()))
    }

    private fun saveInventory() {
        val array = JSONArray()
        products.forEach { p -> array.put(JSONObject().put("id", p.id).put("name", p.name).put("unit", p.unit)
            .put("totalPurchased", p.totalPurchased).put("totalSold", p.totalSold).put("stock", p.stock)
            .put("totalPurchaseCost", p.totalPurchaseCost).put("totalSalesRevenue", p.totalSalesRevenue)
            .put("costOfGoodsSold", p.costOfGoodsSold).put("salePrice", p.salePrice).put("purchasePrice", p.purchasePrice)) }
        prefs.edit().putString("inventory_products", array.toString()).apply()
    }

    private fun loadInventory() {
        try {
            val array = JSONArray(prefs.getString("inventory_products", "[]"))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                products.add(InventoryProduct(o.getLong("id"), o.optString("name"), o.optString("unit", "عدد"),
                    o.optDouble("totalPurchased"), o.optDouble("totalSold"), o.optDouble("stock"),
                    o.optLong("totalPurchaseCost"), o.optLong("totalSalesRevenue"), o.optLong("costOfGoodsSold"), o.optLong("salePrice"), o.optLong("purchasePrice", if (o.optDouble("totalPurchased") > 0) (o.optLong("totalPurchaseCost") / o.optDouble("totalPurchased")).toLong() else 0L)))
            }
        } catch (_: Exception) { }
    }

    private fun qty(value: Double): String {
        val raw = String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
        return raw.toEnglishDigits()
    }

    private fun buildAccountsPage(content: LinearLayout) {
        content.addView(label("حساب‌های باز", 21, appPrimary(), true))
        summary = label("", 16, DARK, true).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { setColor(appPrimary()); cornerRadius = dp(18).toFloat() }
            setTextColor(Color.WHITE)
        }
        content.addView(summary, matchWrap())
        val settlementRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val paySupplier = Button(this).apply {
            text = "پرداخت بدهی فروشنده"
            setTextColor(Color.WHITE); setBackgroundColor(appPrimary())
            setOnClickListener { settlementDialog(true) { refresh() } }
        }
        val receiveCustomer = Button(this).apply {
            text = "دریافت طلب از مشتری"
            setTextColor(Color.WHITE); setBackgroundColor(GOLD)
            setOnClickListener { settlementDialog(false) { refresh() } }
        }
        settlementRow.addView(paySupplier, matchWrap()); settlementRow.addView(receiveCustomer, matchWrap())
        content.addView(settlementRow, matchWrap())

        content.addView(label("بدهکاری و بستانکاری", 18, appPrimary(), true))
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(listBox, matchWrap())
        content.addView(label("فهرست مشتریان", 18, appPrimary(), true))
        val addCustomerButton = Button(this).apply {
            text = "＋ ثبت مشتری جدید"
            setTextColor(Color.WHITE); setBackgroundColor(appPrimary())
            setOnClickListener { addCustomerDialog { refresh() } }
        }
        content.addView(addCustomerButton, matchWrap())
        customersBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(customersBox, matchWrap())
    }

    private fun buildReportsPage(content: LinearLayout) {
        content.addView(label("گزارش‌های مالی", 21, appPrimary(), true))
        summary = label("", 16, DARK, true).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { setColor(appPrimary()); cornerRadius = dp(18).toFloat() }
            setTextColor(Color.WHITE)
        }
        content.addView(summary, matchWrap())
        content.addView(label("بازه گزارش", 15, appPrimary(), true))
        reportRangeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("روزانه", "هفتگی", "ماهانه"))
            setSelection(listOf("روزانه", "هفتگی", "ماهانه").indexOf(reportRange).coerceAtLeast(0))
        }
        content.addView(reportRangeSpinner, matchWrap())
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listBox, matchWrap())
        reportRangeSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                reportRange = listOf("روزانه", "هفتگی", "ماهانه")[position]
                refresh()
            }
        }
    }

    private fun transactionGregorianDate(date: String): Calendar? {
        return try {
            val parts = date.split("/")
            if (parts.size != 3) return null
            val y = parts[0].toInt(); val m = parts[1].toInt(); val d = parts[2].toInt()
            Jalali.toGregorian(y, m, d).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
        } catch (_: Exception) { null }
    }

    private fun transactionsForReport(): List<Tx> {
        val today = Jalali.today()
        val monthPrefix = String.format(Locale.US, "%04d/%02d/", today.year, today.month)
        if (reportRange == "ماهانه") return txs.filter { it.date.startsWith(monthPrefix) }
        val todayGregorian = Jalali.toGregorian(today.year, today.month, today.day).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (reportRange == "روزانه") return txs.filter { it.date == today.format() }
        val weekStart = (todayGregorian.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -(get(Calendar.DAY_OF_WEEK) % 7)) }
        val weekEnd = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 7) }
        return txs.filter { tx ->
            val date = transactionGregorianDate(tx.date) ?: return@filter false
            date.timeInMillis >= weekStart.timeInMillis && date.timeInMillis < weekEnd.timeInMillis
        }
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
        val debtGross = txs.filter { it.kind == "بدهکاری" }.sumOf { it.amount }
        val debtSettled = txs.filter { it.kind == "دریافت طلب" }.sumOf { it.amount }
        val debt = (debtGross - debtSettled).coerceAtLeast(0L)
        val creditGross = txs.filter { it.kind == "بستانکاری" }.sumOf { it.amount }
        val creditSettled = txs.filter { it.kind == "پرداخت بدهی" }.sumOf { it.amount }
        val credit = (creditGross - creditSettled).coerceAtLeast(0L)
        val capital = txs.filter { it.kind == "آورده" }.sumOf { it.amount }
        val cashBalance = income + capital + debtSettled - expense - creditSettled
        val totalBalance = cashBalance + debt - credit

        if (!::summary.isInitialized || !::listBox.isInitialized) return

        val reportTxs = transactionsForReport()
        val reportIncome = reportTxs.filter { it.kind == "واریزی" || it.kind == "آورده" }.sumOf { it.amount }
        val reportExpense = reportTxs.filter { it.kind == "برداشت" }.sumOf { it.amount }
        val reportDebt = reportTxs.filter { it.kind == "بدهکاری" }.sumOf { it.amount }
        val reportCredit = reportTxs.filter { it.kind == "بستانکاری" }.sumOf { it.amount }
        val reportPayDebt = reportTxs.filter { it.kind == "پرداخت بدهی" }.sumOf { it.amount }
        val reportReceiveDebt = reportTxs.filter { it.kind == "دریافت طلب" }.sumOf { it.amount }

        summary.text = when (currentPage) {
            "accounts" ->
                "طلب از دیگران: ${money(debt)} تومان\n" +
                "بدهی به دیگران: ${money(credit)} تومان\n" +
                "اثر خالص حساب‌ها: ${money(debt - credit)} تومان\n" +
                "مانده نقدی: ${money(cashBalance)} تومان\n" +
                "دارایی خالص با طلب و بدهی: ${money(totalBalance)} تومان"
            "reports" ->
                "گزارش ${reportRange}: ${if (reportRange == "روزانه") Jalali.today().format() else if (reportRange == "هفتگی") "هفته جاری" else Jalali.monthName(Jalali.today().month) + " " + Jalali.today().year}\n" +
                "واریزی و آورده: ${money(reportIncome)} تومان\n" +
                "برداشت: ${money(reportExpense)} تومان\n" +
                "طلب: ${money(reportDebt)} تومان\n" +
                "بدهی: ${money(reportCredit)} تومان\n" +
                "پرداخت بدهی: ${money(reportPayDebt)} تومان\n" +
                "دریافت طلب: ${money(reportReceiveDebt)} تومان\n" +
                "تغییر نقدی: ${money(reportIncome + reportReceiveDebt - reportExpense - reportPayDebt)} تومان\n" +
                "مانده خالص با طلب و بدهی: ${money(reportIncome + reportReceiveDebt + reportDebt - reportExpense - reportPayDebt - reportCredit)} تومان\n" +
                "تعداد تراکنش‌ها: ${reportTxs.size}"
            "transactions" ->
                "تعداد تراکنش‌ها: ${txs.size}\n" +
                "مانده نقدی: ${money(cashBalance)} تومان\n" +
                "دارایی خالص با طلب و بدهی: ${money(totalBalance)} تومان"
            else ->
                "کل واریزی: ${money(income)} تومان\n" +
                "آورده شخصی: ${money(capital)} تومان\n" +
                "فروش ثبت‌شده: ${money(products.sumOf { it.totalSalesRevenue })} تومان\n" +
                "کل برداشت: ${money(expense)} تومان\n" +
                "طلب: ${money(debt)} تومان\n" +
                "بدهی: ${money(credit)} تومان\n" +
                "مانده نقدی: ${money(cashBalance)} تومان\n" +
                "دارایی خالص با طلب و بدهی: ${money(totalBalance)} تومان"
        }

        listBox.removeAllViews()
        if (currentPage == "home") {
            val totalKinds = products.size
            val totalUnits = products.sumOf { it.stock }
            val stockValue = products.sumOf { product ->
                val averagePurchase = if (product.totalPurchased > 0) product.totalPurchaseCost / product.totalPurchased else product.purchasePrice.toDouble()
                (product.stock * averagePurchase).toLong()
            }
            val stockDetails = if (products.isEmpty()) "هنوز کالایی ثبت نشده است" else products.joinToString("\n") { "${it.name}: ${qty(it.stock)} ${it.unit}  •  فروش هر واحد ${money(it.salePrice)} تومان" }
            addHomeCard("کالاهای موجود در فروشگاه", "$totalKinds نوع کالا", "مجموع تعداد/وزن: ${qty(totalUnits)}\nارزش موجودی به قیمت خرید: ${money(stockValue)} تومان\n$stockDetails")
            addHomeCard("موجودی نقدی فروشگاه", "${money(cashBalance)} تومان", "موجودی محاسبه‌شده از ثبت‌های مالی")
            addHomeCard("حساب‌های باز فروشگاه", "طلب: ${money(debt)} تومان\nبدهی: ${money(credit)} تومان", "طلب از مشتریان و بدهی به فروشندگان")
            val customerDetails = if (customers.isEmpty()) "هنوز مشتری ثبت نشده است" else customers.joinToString("\n") { customerName ->
                val original = txs.filter { it.kind == "بدهکاری" && it.category.startsWith("طلب از مشتری: $customerName |") }.sumOf { it.amount }
                val received = txs.filter { it.kind == "دریافت طلب" && it.category == "دریافت از مشتری: $customerName" }.sumOf { it.amount }
                "$customerName: ${money((original - received).coerceAtLeast(0L))} تومان"
            }
            addHomeCard("بدهی مشتریان", "${money(debt)} تومان", customerDetails)
            return
        }
        if (currentPage == "accounts" && ::customersBox.isInitialized) {
            customersBox.removeAllViews()
            if (customers.isEmpty()) customersBox.addView(label("هنوز مشتری ثبت نشده است.", 13, Color.GRAY))
            customers.forEach { customerName ->
                val customerDebt = txs.filter { it.kind == "بدهکاری" && it.category.startsWith("طلب از مشتری: $customerName |") }.sumOf { it.amount }
                val received = txs.filter { it.kind == "دریافت طلب" && it.category == "دریافت از مشتری: $customerName" }.sumOf { it.amount }
                val remaining = (customerDebt - received).coerceAtLeast(0L)
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(label("$customerName  |  طلب مانده: ${money(remaining)} تومان", 14, DARK), LinearLayout.LayoutParams(0, -2, 1f))
                val receive = Button(this).apply { text = "دریافت"; setOnClickListener { settlementDialog(false) { refresh() } } 
                }
                row.addView(receive); customersBox.addView(row, matchWrap())
            }
        }

        val visibleTxs = when (currentPage) {
            "home" -> txs.take(5)
            "accounts" -> txs.filter {
                it.kind == "بدهکاری" || it.kind == "بستانکاری" || it.kind == "پرداخت بدهی" || it.kind == "دریافت طلب"
            }
            "reports" -> reportTxs
            else -> txs
        }

        if (visibleTxs.isEmpty()) {
            val emptyMessage = when (currentPage) {
                "accounts" -> "هنوز بدهکاری یا بستانکاری ثبت نشده است."
                "reports" -> "برای این بازه تراکنشی ثبت نشده است."
                else -> "هنوز تراکنشی ثبت نشده است."
            }
            listBox.addView(label(emptyMessage, 14, Color.GRAY))
            return
        }

        visibleTxs.forEach { transaction ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(14).toFloat() }
            }

            val titleText = if (transaction.kind == "کالا") {
                "کالا: ${transaction.category}"
            } else {
                "${transaction.kind} | ${money(transaction.amount)} تومان"
            }
            card.addView(label(titleText, 16, appPrimary(), true))
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

            if (transaction.note.isNotBlank() && !transaction.note.startsWith("INV|")) {
                card.addView(label(transaction.note, 14, Color.GRAY))
            }

            if (transaction.note.startsWith("INV|")) {
                card.addView(label("ثبت خودکار موجودی؛ برای تغییر از بخش لیست کالاها استفاده کن", 12, Color.GRAY))
                listBox.addView(card, matchWrap())
                listBox.addView(View(this).apply { setBackgroundColor(0xFFE5E1D8.toInt()) }, LinearLayout.LayoutParams(-1, dp(1)))
                return@forEach
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
            setBackgroundColor(appBackground())
        }

        val title = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 18f * appFontScale()
            setTextColor(appPrimary())
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
                        setTextColor(appPrimary())
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
                        textSize = 15f * appFontScale()
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
        textSize = size.toFloat() * appFontScale()
        setTextColor(if (color == GREEN) appPrimary() else color)
        setTypeface(appTypeface(), if (bold) Typeface.BOLD else Typeface.NORMAL)

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
