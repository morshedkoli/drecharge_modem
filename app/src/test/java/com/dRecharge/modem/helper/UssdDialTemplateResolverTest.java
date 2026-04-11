package com.dRecharge.modem.helper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UssdDialTemplateResolverTest {

    @Test
    public void resolveCustomDialCode_returnsEmptyWhenCustomDisabled() {
        ServiceConfig config = new ServiceConfig("Grameen");
        config.customUssdEnabled = false;
        config.dialCode1 = "*444*{PHONE}*{AMOUNT}*0*{PIN}#";

        String dialCode = UssdDialTemplateResolver.resolveCustomDialCode(
                config, "1", "01700000000", "50", "1234");

        assertEquals("", dialCode);
    }

    @Test
    public void resolveCustomDialCode_usesTypeZeroTemplateWhenAvailable() {
        ServiceConfig config = new ServiceConfig("Grameen");
        config.customUssdEnabled = true;
        config.dialCode0 = "*111*{PHONE}*{AMOUNT}*{PIN}#";
        config.dialCode1 = "*444*{PHONE}*{AMOUNT}*0*{PIN}#";

        String dialCode = UssdDialTemplateResolver.resolveCustomDialCode(
                config, "0", "01700000000", "75", "4321");

        assertEquals("*111*01700000000*75*4321#", dialCode);
    }

    @Test
    public void resolveCustomDialCode_fallsBackToPrimaryTemplateForAllServices() {
        ServiceConfig config = new ServiceConfig("bKash-Personal-SIM");
        config.customUssdEnabled = true;
        config.dialCode1 = "*247*1*{PHONE}*{AMOUNT}*{PIN}#";

        String dialCode = UssdDialTemplateResolver.resolveCustomDialCode(
                config, "1", "01800000000", "150", "9876");

        assertEquals("*247*1*01800000000*150*9876#", dialCode);
    }

    @Test
    public void applyTemplate_handlesNullInputsWithoutCrashing() {
        String dialCode = UssdDialTemplateResolver.applyTemplate(
                "*444*{PHONE}*{AMOUNT}*0*{PIN}#", null, "20", null);

        assertEquals("*444**20*0*#", dialCode);
    }
}
