<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <style>
      .invoice-box {
          max-width: 800px;
          margin: auto;
          padding: 15px;
          font-family: 'Helvetica Neue', 'Helvetica', Helvetica, Arial, sans-serif;
          color: #555;
      }

      .invoice-box table {
          width: 100%;
          text-align: left;
      }

      .invoice-box table td {
          font-size: 13px;
          vertical-align: top;
          padding: 2px;
      }

      .invoice-box table tr.top table td {
          padding-bottom: 20px;
      }

      .invoice-box table tr.top table td.title {
          font-size: 45px;
          color: #333;
      }

      .invoice-box table tr.information table td {
          padding-bottom: 40px;
          font-size: 12px;
      }

      .invoice-box table tr.heading td {
          background: #eee;
          border: 1px solid #ddd;
          font-weight: bold;
          font-size: 12px;
      }

      .invoice-box table tr.heading p {
          background: #eee;
          border: 1px solid #ddd;
          font-weight: bold;
      }

      .invoice-box table tr.details td {
          padding-bottom: 20px;
          font-size: 12px;
      }

      .invoice-box table tr.item td {
          border: 1px solid #ddd;
          font-size: 12px;
      }

      .invoice-box table tr.item.last td {
          border: 1px solid #ddd;
      }

      .center {
          text-align: center;
      }

      .hr {
          margin-top: 40px;
          border-top: 1px solid #ddd;
      }

      .dosage table tr.dosage-heading td {
          background: #3b4f60;
          border: 1px solid #ddd;
          font-weight: bold;
          color: #ffffff;
          padding: 10px;
      }

      .dosage table tr.dosage-col td.table {
          border: 1px solid #ddd;
          padding: 10px;
          color: #4f4f51;
          font-size: 12px;
      }

      .dosage table tr:nth-child(even) {
          background-color: #dcf0ed;
      }

      .invoice-box table td .sub-table td {
          border: 0;
      }

      a:link {
          color: #4f4f51;
      }

      @media only screen and (max-width: 600px) {
          .invoice-box table tr.top table td {
              width: 100%;
              display: block;
              text-align: center;
          }
          .invoice-box table tr.information table td {
              width: 100%;
              display: block;
          }
      }

      @media print {
          body {
              -webkit-print-color-adjust: exact;
          }
      }

      .invoice-table td,
      .invoice-table {
          border: 1px solid #ccc;
      }
  </style>
</head>
<#assign selling_price = Order.SellingPrice?number />
<#assign product_selling_price = Order.ProductSellingPrice?number />
<#assign shipping_selling_price = Order.ShippingSellingPrice?number />
<#assign shipping_price = Order.ShippingSellingPrice?number / (1.18) />
<#if (Order.CGst?number == 0) >
    <#assign shipping_cgst =  0 />
    <#assign shipping_igst =  (((0.18 * shipping_price * 100)?round) / 100) />
<#else>
    <#assign shipping_cgst =  (((0.09 * shipping_price * 100)?round) / 100) />
    <#assign shipping_igst =  0 />
</#if>
<#assign shipping_sgst=shipping_cgst/>
<#assign shipping_line = {"Sku": {"Name": "Fixed Delivery Charges" , "MaxRetailPrice" : shipping_selling_price } , "PackedQuantity" : 1 , "Hsn" : "996813" , "CGst" :  shipping_cgst, "SGst" : shipping_sgst , "IGst" : shipping_igst , "MaxRetailPrice": shipping_selling_price , "SellingPrice" : shipping_selling_price , "Price" : shipping_price , "DiscountPercentage" :"0"   }/>

<#assign qty = 0 />
<#assign mrp = 0 />
<#assign lines=Order.OrderLines?sort_by("MaxRetailPrice")/>
<#assign lines=lines + [shipping_line] />
<#list lines>
<#items as OrderLine>
        <#assign qty += OrderLine.PackedQuantity?number />
        <#assign mrp += OrderLine.MaxRetailPrice?number />
</#items>
</#list>

<#assign discounted_price = Order.Price?number />
<#if discounted_price == 0>
    <#assign otaxRate = 18 />
<#else>
    <#assign otaxRate = (((Order.CGst?number + Order.IGst?number + Order.SGst?number)*100.0/discounted_price)?round) />
</#if>
<#assign oigstRate = otaxRate />
<#assign ocgstRate = otaxRate/2.0 />
<#assign osgstRate = otaxRate/2.0 />
<#assign pre_tax_price = (mrp / (1 + otaxRate/100.0))?round />
<#assign discount = pre_tax_price - discounted_price />
<#assign discount_percent = 0 />
<#if ( discount > 0 ) >
    <#assign discount_percent = (((discount * 100) / pre_tax_price )?round) />
</#if>
<body>
          <div class="invoice-box">
            <table cellpadding="0" border="1" class="invoice-table" cellspacing="0">
              <tr class="top" width="50%">
                 <td colspan="2" style="text-align: center; font-size: 15px;">
        				    <strong>Invoice</strong>
                 </td>
               </tr>
               <tr>
                 <td>
                     <strong>Sold By:</strong><br />
                     <#if ShipFrom.Name??>
                         ${ShipFrom.Name},<br/>
                     </#if>
                     <#if ShipFrom.AddressLine1??>
                         ${ShipFrom.AddressLine1},<br/>
                     </#if>
                     <#if ShipFrom.AddressLine2??>
                         ${ShipFrom.AddressLine2},<br/>
                     </#if>
                     <#if ShipFrom.AddressLine3??>
                         ${ShipFrom.AddressLine3},<br/>
                     </#if>
                     <#if ShipFrom.AddressLine4??>
                         ${ShipFrom.AddressLine4},
                     </#if>
                     <#if ShipFrom.City.Name??>
                         <br/>${ShipFrom.City.Name}
                     </#if>
                     <#if ShipFrom.State.Name??>
                         , ${ShipFrom.State.Name},
                     </#if>
                     <#if ShipFrom.Country.Name??>
                          ${ShipFrom.Country.Name},
                     </#if>
                     <#if ShipFrom.PinCode.PinCode??>
                         ${ShipFrom.PinCode.PinCode}.<br/>
                     </#if>
                     State Code: <#if ShipFrom.State.Code??>${ShipFrom.State.Code}</#if>
                     <br/>
                        <#if ShipFrom.Company.GSTIN??>
                            GSTIN: ${ShipFrom.Company.GSTIN}
                        <#elseif ShipFrom.GSTIN?? >
                            GSTIN: ${ShipFrom.GSTIN}
                        </#if>
                 </td>
              <td>
              <table cellspacing="0" class="sub-table" cellpadding="0">
                  <tr>
                      <td>Order Number: ${Order.OrderNumber}</td>
                      <td></td>
                  </tr>
              </table>
            </td>
        </tr>

          <tr>
              <td align="center">Billing Address</td>
              <td align="center">Shipping Address</td>
          </tr>

          <tr>
              <td>
                <#if BillTo.FirstName??>
                    ${BillTo.FirstName}
                    <#if BillTo.LastName??>
                        ${BillTo.LastName}<br/>
                    <#else>
                        <br/>
                    </#if>
                </#if>

                <#if BillTo.AddressLine1??>
                    ${BillTo.AddressLine1},
                </#if>
                <#if BillTo.AddressLine2??>
                    ${BillTo.AddressLine2},
                </#if>
                <#if BillTo.AddressLine3??>
                    ${BillTo.AddressLine3},
                </#if>
                <#if BillTo.AddressLine4??>
                    ${BillTo.AddressLine4},
                </#if>
                <#if BillTo.City.Name??>
                    <br/>${BillTo.City.Name},
                </#if>
                <#if BillTo.State.Name??>
                    ${BillTo.State.Name},
                </#if>
                <#if BillTo.Country.Name??>
                    ${BillTo.Country.Name},
                </#if>
                <#if BillTo.PinCode.PinCode??>
                    <br/>${BillTo.PinCode.PinCode}.
                </#if>
                <#if BillTo.PhoneNumber??>
                    <br/>${BillTo.PhoneNumber}.
                </#if>
                <#if BillTo.Email??>
                    <br/>${BillTo.Email}.
                </#if>
              </td>

              <td>
                <#if ShipTo.FirstName??>
                    ${ShipTo.FirstName}
                    <#if ShipTo.LastName??>
                        ${ShipTo.LastName}<br/>
                    <#else>
                        <br/>
                    </#if>
                </#if>
                <#if ShipTo.AddressLine1??>
                    ${ShipTo.AddressLine1},
                </#if>
                <#if ShipTo.AddressLine2??>
                    ${ShipTo.AddressLine2},
                </#if>
                <#if ShipTo.AddressLine3??>
                    ${ShipTo.AddressLine3},
                </#if>
                <#if ShipTo.AddressLine4??>
                    ${ShipTo.AddressLine4},
                </#if>
                <#if ShipTo.City.Name??>
                    <br/>${ShipTo.City.Name},
                </#if>
                <#if ShipTo.State.Name??>
                    ${ShipTo.State.Name},
                </#if>
                <#if ShipTo.Country.Name??>
                     ${ShipTo.Country.Name},
                </#if>
                <#if ShipTo.PinCode.PinCode??>
                    <br/>${ShipTo.PinCode.PinCode}.
                </#if>
                <#if ShipTo.PhoneNumber??>
                    <br/>${ShipTo.PhoneNumber}.
                </#if>
                <#if ShipTo.Email??>
                    <br/>${ShipTo.Email}.
                </#if>
              </td>
          </tr>


 <tr>
  <td colspan="2" style="padding: 0 !important;">
    <table cellpadding="0" cellspacing="0">
      <tr class="heading" style="text-align: center">
        <td colspan="1" rowspan="2" style="max-height: 22px; height: 22px; overflow: auto;">
            SKU
        </td>
        <td colspan="1" rowspan="2">
            HSN
        </td>
        <td colspan="1" rowspan="2">
            QTY
        </td>
        <td colspan="1" rowspan="2">
            UNIT PRICE
        </td>
        <td colspan="1" rowspan="2">
            TAXABLE VALUE
        </td>
        <td colspan="2">
            CGST
        </td>
        <td colspan="2">
            SGST/UGST
        </td>
        <td colspan="2">
            IGST
        </td>
        <td colspan="1" rowspan="2">
            TOTAL
        </td>
    </tr>
    <tr class="item" style="text-align:right">
      <td>Rate</td>
      <td>
          Amt
      </td>
      <td>
          Rate
      </td>
      <td>
          Amt
      </td>
      <td>
          Rate
      </td>
      <td>
          Amt
      </td>
    </tr>
    <#list lines>
     <#items as OrderLine>
        <#assign ldiscounted_price = OrderLine.Price?number />
        <#if ldiscounted_price == 0>
            <#assign ltaxRate = 18 >
        <#else>
            <#if ((((OrderLine.CGst?number + OrderLine.IGst?number + OrderLine.SGst?number)*100.0/ldiscounted_price)?round) < 18) >
                <#assign ltaxRate =  (((OrderLine.CGst?number + OrderLine.IGst?number + OrderLine.SGst?number)*100.0/ldiscounted_price)?round)/>
            <#else>
                <#assign ltaxRate =  18/>
         </#if>
        </#if>
        <#assign ligstRate = ltaxRate />
        <#assign lcgstRate = ltaxRate/2.0 />
        <#assign lsgstRate = ltaxRate/2.0 />
        <#assign lqty = OrderLine.PackedQuantity?number />
        <#assign lmrp = OrderLine.MaxRetailPrice?number />
        <#assign lpre_tax_price = ((lmrp / (1 + ltaxRate/100.0) )?round) />
        <#assign lselling_price = OrderLine.SellingPrice?number />
        <tr class="item last" style="text-align:right">
      <td style="text-align: left;  max-height: 22px; height: 22px; overflow: auto;" >${OrderLine.Sku.Name}</td>
      <td>${OrderLine.Hsn}</td>
      <td>${lqty}</td>
      <td>${(ldiscounted_price/lqty)?string["0.00"]}</td>
      <td>${ldiscounted_price?string["0.00"]}</td>
      <td>
        <#if OrderLine.CGst?number == 0>
            0%
        <#else>
            ${lcgstRate}%
        </#if>
      </td>
      <td colspan="1">
          <#if OrderLine.CGst?number == 0>
              0
          <#else>
              ${OrderLine.CGst?number?string["0.00"]}
          </#if>
      </td>
      <td>
        <#if OrderLine.SGst?number == 0>
            0%
        <#else>
            ${lsgstRate}%<br/>
        </#if>
      </td>
      <td colspan="1">
          <#if OrderLine.SGst?number == 0>
              0
          <#else>
              ${OrderLine.SGst?number?string["0.00"]} <br/>
          </#if>
      </td>
      <td>
        <#if OrderLine.IGst?number == 0>
            0%
        <#else>
            ${ligstRate}%
        </#if>
      </td>
      <td colspan="1">
          <#if OrderLine.IGst?number == 0>
              0
          <#else>
              ${OrderLine.IGst?number?string["0.00"]}
          </#if>
      </td>
      <td>${lselling_price?string["0.00"]}</td>
    </tr>
</#items>
</#list>
    <tr class="item" style="text-align:right">
      <td style="text-align: left;  max-height: 22px; height: 22px; overflow: auto;"><b>TOTAL</b></td>
      <td><b>-</b></td>
      <td><b>-</b></td>
      <td><b>-</b></td>
      <td><b>${discounted_price?string["0.00"]}</b></td>
      <td>
        <#if Order.CGst?number == 0>
            0%
        <#else>
            ${ocgstRate}%<br/>
        </#if>
      </td>
      <td colspan="1">
          <#if Order.CGst?number == 0>
              0
          <#else>
              ${((Order.CGst?number * 100)?round/ 100)?string["0.00"]}<br/>
          </#if>
      </td>
      <td>
        <#if Order.SGst?number == 0>
            0%
        <#else>
              ${osgstRate}%<br/>
        </#if>
      </td>
      <td colspan="1">
          <#if Order.SGst?number == 0>
              0
          <#else>
              ${((Order.SGst?number * 100)?round/ 100)?string["0.00"]}<br/>
          </#if>
      </td>
      <td>
        <#if Order.IGst?number == 0>
            0%
        <#else>
            ${oigstRate}%<br/>
        </#if>
      </td>
      <td colspan="1">
          <#if Order.IGst?number == 0>
              0
          <#else>
              ${((Order.IGst?number * 100)?round/ 100)?string["0.00"]}<br/>
          </#if>
      </td>
      <td><b>${selling_price?string["0.00"]}</b></td>
     </tr>
    </table>
   </td>
 </tr>

  <tr>
    <td colspan="2" class="heading" style="text-align: left; font-size:12px;">
     <strong>Total Amount (in words): <@to_words>${selling_price}</@to_words>.</strong>
    </td>
  <tr/>


  <tr>
   <td colspan="1" style="padding: 10px !important; border:0;">
      <table cellpadding="0" cellspacing="0">
           <tr class="heading">
             <td colspan="7" style="text-align: center; font-size: 12px;">
                 GST SUMMARY
             </td>
           </tr>
                   <tr class="item">
                    <td rowspan=2 style="text-align: center; font-size: 12px;">Taxable<br/> Value</td>
                    <td  colspan="2" style="text-align: center; font-size: 12px;">CGST</td>
                    <td  colspan="2" style="text-align: center; font-size: 12px;">SGST/UGST</td>
                    <td  colspan="2" style="text-align: center; font-size: 12px;">IGST</td>
                   </tr>
            <tr class="item">
              <td  style="text-align: center; font-size: 12px;">Rate</td>
              <td  style="text-align: center; font-size: 12px;">Amt</td>
              <td  style="text-align: center; font-size: 12px;">Rate</td>
              <td  style="text-align: center; font-size: 12px;">Amt</td>
              <td  style="text-align: center; font-size: 12px;">Rate</td>
              <td  style="text-align: center; font-size: 12px;">Amt</td>
            </tr>
            <tr class="item">
              <td style="text-align: right; font-size: 12px;">${discounted_price}</td>
              <td style="text-align: right; font-size: 12px;"><#if Order.CGst?number == 0>
                  0
              <#else>
                 ${ocgstRate}
              </#if>%</td>
              <td style="text-align: right; font-size: 12px;"><#if Order.CGst?number == 0>
                  0
              <#else>
                  ${(Order.CGst?number * 100 / 100)}
              </#if></td>
              <td style="text-align: right; font-size: 12px;">  <#if Order.SGst?number == 0>
                    0
                <#else>
                    ${osgstRate}
                </#if>%</td>
              <td style="text-align: right; font-size: 12px;">  <#if Order.SGst?number == 0>
                    0
                <#else>
                    ${(Order.SGst?number * 100 / 100)} <br/>
                </#if></td>
              <td style="text-align: right; font-size: 12px;"><#if Order.IGst?number == 0>
                  0
              <#else>
                  ${oigstRate}
              </#if>%</td>
              <td style="text-align: right; font-size: 12px;"><#if Order.IGst?number == 0>
                  0
              <#else>
                  ${(Order.IGst?number * 100 / 100)}
              </#if>
          </td>
            </tr>
      </table>
    </td>
    <td colspan="1" style="border:0;">
      <table cellspacing="0" class="sub-table" cellpadding="0" style="padding: 10px !important;" >
      <tr>
        <td style="text-align:right">
          Total Amount:
        </td>
        <td style="text-align:right">
          ${((discounted_price * 100.0)?round/100.0)?string["0.00"]}
        </td>
       </tr>
       <tr>

         <#assign cgst = Order.CGst?number />
         <#assign sgst = Order.SGst?number />
         <#assign igst = Order.IGst?number />
         <#assign totalTax = cgst + sgst + igst />


         <td style="text-align:right">
            Tax (Inclusive):
         </td>
         <td style="text-align:right">
           ${((totalTax *100.0)?round/100.0)?string["0.00"]}
         </td>
        </tr>

        <tr>
          <td style="text-align:right">Grand Total(INR):
          </td>
          <td style="text-align:right">
            ${((selling_price *100.0)?round/100.0)?string["0.00"]}
          </td>
         </tr>
      </table>
    </td>
  </tr>

  <!--
  <tr>
      <td colspan="2">
         <table style="height: 100px;">
           <tr>
             <td colspan="1" style="border:0; padding-top: 90px;">
                Prepared By
             </td>
             <td colspan="1" style="border:0; padding-top: 90px;">
                Verified By
             </td>
             <td colspan="1" style="border:0; padding-top: 90px;">
                Authorised Signature
             </td>
          </tr>
        </table>
      </td>
  </tr>
  -->

  <tr class="information">
      <td colspan="2">
       <b>
        <!--
         Head Office: Sixth Sense Retail Pvt. Ltd., Baner, Pune, Maharashtra - 411 045<br/> India<br/>
         Customer care: <a href="tel: +91 8431620000" target="_blank">+91 8431620000</a>, Email us: <a href="mailto: support@wiggles.in" target="_blank">support@wiggles.in</a><br/>
         -->
       </b>
      </td>
  </tr>
  <tr>
     <td colspan="2">
       <b>
        This is computer generated invoice. No signature required.
        Thank you for entrusting us!
       </b>
    </td>
  </tr>
 </table>
</div>
</body>
</html>
