package bassamalim.halala.core.sms

import bassamalim.halala.core.enums.Direction.CREDIT
import bassamalim.halala.core.enums.Direction.DEBIT
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionKind.ATM_WITHDRAWAL
import bassamalim.halala.core.enums.TransactionKind.BILL_PAYMENT
import bassamalim.halala.core.enums.TransactionKind.FEE
import bassamalim.halala.core.enums.TransactionKind.INTERNAL_TRANSFER
import bassamalim.halala.core.enums.TransactionKind.INVESTMENT_BUY
import bassamalim.halala.core.enums.TransactionKind.INVESTMENT_SELL
import bassamalim.halala.core.enums.TransactionKind.OTHER
import bassamalim.halala.core.enums.TransactionKind.PURCHASE
import bassamalim.halala.core.enums.TransactionKind.REFUND
import bassamalim.halala.core.enums.TransactionKind.SALARY
import bassamalim.halala.core.enums.TransactionKind.SAVINGS_DEPOSIT
import bassamalim.halala.core.enums.TransactionKind.TRANSFER_IN
import bassamalim.halala.core.enums.TransactionKind.TRANSFER_OUT
import bassamalim.halala.core.sms.Role.ACCOUNT
import bassamalim.halala.core.sms.Role.AMOUNT
import bassamalim.halala.core.sms.Role.AT
import bassamalim.halala.core.sms.Role.BALANCE
import bassamalim.halala.core.sms.Role.CARD
import bassamalim.halala.core.sms.Role.FROM
import bassamalim.halala.core.sms.Role.PARTY
import bassamalim.halala.core.sms.Role.TO
import bassamalim.halala.core.sms.Role.TOTAL

/**
 * How each v1 bank writes its SMS, collected from a real inbox. Each format is covered by the
 * fixtures in `src/test/resources/sms`; a new layout from a bank is a new template or label
 * here plus a fixture there. Bump [PARSER_VERSION] with any change, so stored messages are
 * re-parsed.
 */
object BankFormats {

    const val PARSER_VERSION = 1

    private fun out(header: String, kind: TransactionKind) = Template(header, kind, DEBIT)
    private fun into(header: String, kind: TransactionKind) = Template(header, kind, CREDIT)
    /** A transfer whose header doesn't say which way it went. */
    private fun transfer(header: String) = Template(header, TRANSFER_OUT)
    private fun declined(header: String) = Template(header, declined = true)
    /** A move between your own accounts; older SMS name only where it went, so it reads as a credit. */
    private fun between(header: String) = Template(header, INTERNAL_TRANSFER)
    private fun ignore(header: String) = Template(header)

    val AL_RAJHI = BankFormat(
        institution = "Al Rajhi",
        senders = setOf("AlRajhiBank"),
        templates = listOf(
            out("شراء", PURCHASE),
            out("خصم من التفويض", PURCHASE),
            // The hold before a card capture; the capture ("خصم من التفويض") is the purchase.
            ignore("تفويض"),
            into("استرداد شراء", REFUND),
            into("عكس عملية", REFUND),
            transfer("حوالة محلية"),
            into("حوالة محلية واردة", TRANSFER_IN),
            out("حوالة محلية صادرة", TRANSFER_OUT),
            transfer("حوالة داخلية"),
            into("حوالة داخلية واردة", TRANSFER_IN),
            out("حوالة داخلية صادرة", TRANSFER_OUT),
            into("اضافه حوالة داخلية", TRANSFER_IN),
            between("حوالة بين حساباتك"),
            into("حوالة واردة راتب", SALARY),
            into("ايداع:الأرباح", OTHER),
            out("سحب:صراف", ATM_WITHDRAWAL),
            out("تحويل Urpay", TRANSFER_OUT),
            // Each Awaeed deposit is its own numberless account at the bank; they are kept as one.
            out("انشاء حساب عوائد", SAVINGS_DEPOSIT).copy(into = "Awaeed"),
            out("تحويل الى حساب الراجحي المالية", TRANSFER_OUT).copy(farBank = "Al Rajhi Capital"),
            into("تحويل من حساب الراجحي المالية", TRANSFER_IN).copy(farBank = "Al Rajhi Capital"),
            out("مدفوعات", BILL_PAYMENT),
            out("سداد فاتورة", BILL_PAYMENT),
            out("خصم:رسوم", FEE),
            // A confirmation of a transfer that has its own SMS.
            ignore("عزيزي العميل تم تعميد حوالتكم"),
            ignore("تم تغيير حد التحويل")
        ),
        labels = mapOf(
            "مبلغ" to AMOUNT,
            "المبلغ" to AMOUNT,
            "بـ" to AMOUNT,
            "من" to FROM,
            "الى" to TO,
            "إلى" to TO,
            "لـ" to TO,
            "المستفيد" to TO,
            "بطاقة" to CARD,
            "عبر" to CARD,
            "لدى" to AT,
            "الجهة" to AT,
            "الخدمة" to AT,
            "مكان السحب" to AT,
            "رسوم" to Role.FEE,
            "الرسوم" to Role.FEE,
            "رسوم التوصيل" to Role.FEE
        )
    )

    val SNB = BankFormat(
        institution = "SNB",
        senders = setOf("SNB-AlAhli"),
        templates = listOf(
            out("شراء", PURCHASE),
            into("اعادة شراء", REFUND),
            into("عكس شراء", REFUND),
            into("استرداد مبلغ شراء", REFUND),
            into("عكس تحويل", REFUND),
            into("حوالة واردة", TRANSFER_IN),
            into("حوالة داخلية واردة", TRANSFER_IN),
            into("عملية حوالة مالية واردة", TRANSFER_IN),
            into("عملية نقل رصيد", TRANSFER_IN),
            out("حوالة صادرة", TRANSFER_OUT),
            out("حوالة فورية محلية صادرة", TRANSFER_OUT),
            between("حوالة بين حساباتك"),
            into("ايداع رواتب", SALARY),
            into("مستحقات أخرى من صاحب العمل", SALARY),
            out("سحب", ATM_WITHDRAWAL),
            out("سداد فاتورة", BILL_PAYMENT),
            out("مدفوعات", BILL_PAYMENT),
            out("خصم رسوم", FEE),
            declined("اشعار رصيد غير كافي")
        ),
        labels = mapOf(
            "مبلغ" to AMOUNT,
            "بمبلغ" to AMOUNT,
            "بـ" to AMOUNT,
            "القيمة" to AMOUNT,
            "من" to FROM,
            "مرسل" to FROM,
            "من حساب" to PARTY,
            "خصمت بنجاح من حساب" to ACCOUNT,
            "إيداع في حساب" to ACCOUNT,
            "اسم المتجر" to AT,
            "إلى" to TO,
            "الى" to TO,
            "ل" to TO,
            "مستفيد" to TO,
            "آيبان" to PARTY,
            "حساب" to ACCOUNT,
            "الحساب" to ACCOUNT,
            "بطاقة" to CARD,
            "مدى" to CARD,
            "موقع" to AT,
            "الجهة" to AT,
            "الخدمة" to AT,
            "رسوم" to Role.FEE
        )
    )

    /** stc bank, which also sent as STCPAY when it was stc pay. */
    val STC_BANK = BankFormat(
        institution = "STC Bank",
        senders = setOf("STC Bank", "STCPAY"),
        templates = listOf(
            out("VISA Purchase", PURCHASE),
            out("Online Purchase", PURCHASE),
            out("International Purchase", PURCHASE),
            out("***", PURCHASE),
            out("stc prepaid services payment", BILL_PAYMENT),
            out("Wallet Operations", BILL_PAYMENT),
            into("Adding money to account", TRANSFER_IN),
            into("STC pay wallet top up", TRANSFER_IN),
            into("Transaction type: wallet topup", TRANSFER_IN),
            into("Wallet top up", TRANSFER_IN),
            into("تغذية محفظة", TRANSFER_IN),
            into("Inward transfer", TRANSFER_IN),
            into("Credit Local transfer", TRANSFER_IN),
            into("Internal incoming transfer", TRANSFER_IN),
            into("Incoming internal transfer", TRANSFER_IN),
            into("Credit via wallet transfer", TRANSFER_IN),
            out("Internal transfer", TRANSFER_OUT),
            out("Internal outward transfer", TRANSFER_OUT),
            out("Outward transfer", TRANSFER_OUT),
            out("debit internal transfer", TRANSFER_OUT),
            out("Debit internal transfer", TRANSFER_OUT),
            out("Debit via wallet transfer", TRANSFER_OUT),
            out("Debit local transfer", TRANSFER_OUT),
            into("Reverse", REFUND),
            into("Notification: Refund", REFUND),
            into("Refund", REFUND),
            declined("Insufficient balance"),
            declined("Notification: Declined"),
            declined("Declined")
        ),
        labels = mapOf(
            "Amount" to AMOUNT,
            "Transaction Amount" to AMOUNT,
            "المبلغ" to AMOUNT,
            "With" to AMOUNT,
            "From" to FROM,
            "Sender name" to FROM,
            "Sender account" to FROM,
            "To" to TO,
            "Receiver name" to TO,
            "Receiver account" to TO,
            "Via" to CARD,
            "Card" to CARD,
            "Card Number" to CARD,
            "Acc" to PARTY,
            // "Account *666" in a SARIE transfer is yours, whichever way it went.
            "Account" to ACCOUNT,
            "Account number" to ACCOUNT,
            "At" to AT,
            "For" to AT,
            "Transaction" to AT,
            "Fees" to Role.FEE,
            "Total due amount" to TOTAL,
            "Remaining balance" to BALANCE
        )
    )

    val D360 = BankFormat(
        institution = "D360",
        senders = setOf("D360 Bank"),
        templates = listOf(
            out("Local Online Purchase", PURCHASE),
            out("Local POS Purchase", PURCHASE),
            into("Incoming Transfer", TRANSFER_IN),
            out("Outgoing", TRANSFER_OUT),
            declined("Transaction Declined")
        ),
        labels = mapOf(
            "Amount" to AMOUNT,
            "From" to FROM,
            "To" to TO,
            "IBAN" to TO,
            "Card" to CARD,
            "Account number" to ACCOUNT,
            "At" to AT
        )
    )

    val BARQ = BankFormat(
        institution = "Barq",
        senders = setOf("barq app"),
        templates = listOf(
            into("Money Added", TRANSFER_IN),
            into("Barq wallet transfer", TRANSFER_IN),
            out("Debit Transfer", TRANSFER_OUT)
        ),
        labels = mapOf(
            "Amount" to AMOUNT,
            "From" to FROM,
            "To" to TO,
            "To A/C" to PARTY,
            "card number" to CARD
        )
    )

    val AL_RAJHI_CAPITAL = BankFormat(
        institution = "Al Rajhi Capital",
        senders = setOf("ALRajhiCPTL"),
        templates = listOf(
            out("Subscription Order", INVESTMENT_BUY),
            into("Redemption Order", INVESTMENT_SELL),
            into("Local transfer", TRANSFER_IN),
            into("Add Money", TRANSFER_IN),
            out("Local Transfer", TRANSFER_OUT),
            out("حوالة محلية", TRANSFER_OUT)
        ),
        labels = mapOf(
            "Amount" to AMOUNT,
            "المبلغ" to AMOUNT,
            "From" to FROM,
            "من" to FROM,
            "To" to TO,
            "إلى" to TO,
            "Card number" to CARD
        )
    )

    val ALL = listOf(AL_RAJHI, SNB, STC_BANK, D360, BARQ, AL_RAJHI_CAPITAL)
}
