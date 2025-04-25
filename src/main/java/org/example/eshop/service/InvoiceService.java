package org.example.eshop.service;

import org.example.eshop.model.Order;

public interface InvoiceService {

    /**
     * Vygeneruje ZÁLOHOVOU fakturu (proforma) pro objednávku vyžadující zálohu.
     * Voláno typicky hned po vytvoření objednávky na míru.
     *
     * @param order Objednávka.
     */
    void generateProformaInvoice(Order order);

    /**
     * Vygeneruje DAŇOVÝ DOKLAD K PŘIJATÉ PLATBĚ (záloze).
     * Může být implementováno jako ostrá faktura pouze na výši zálohy.
     * Voláno po potvrzení přijetí platby zálohy.
     *
     * @param order Objednávka se zaplacenou zálohou.
     */
    void generateTaxDocumentForDeposit(Order order);


    /**
     * Vygeneruje FINÁLNÍ (ostrou) fakturu pro objednávku.
     * Měla by zohlednit případnou zaplacenou zálohu.
     * Voláno typicky při expedici (pokud je zaplaceno/COD).
     * Původní metoda přejmenována pro jasnost.
     *
     * @param order Objednávka k finální fakturaci.
     */
    void generateFinalInvoice(Order order);

}