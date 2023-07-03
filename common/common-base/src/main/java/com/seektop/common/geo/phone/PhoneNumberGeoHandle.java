package com.seektop.common.geo.phone;

import com.google.i18n.phonenumbers.PhoneNumberOfflineGeocoder;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
public class PhoneNumberGeoHandle {

    public static void main(String[] args) {
        PhoneNumberGeoHandle handle = new PhoneNumberGeoHandle();
        System.out.println(handle.getChineseName("18812361236", 86));
    }

    /**
     * 获取中国区的手机号码归属地
     *
     * @param phoneNumber
     * @return
     */
    public String getChineseName(final String phoneNumber, final Integer countryCode) {
        String location = "";
        try {
            long phone = Long.parseLong(phoneNumber);
            Phonenumber.PhoneNumber pn = new Phonenumber.PhoneNumber();
            pn.setCountryCode(countryCode);
            pn.setNationalNumber(phone);
            if (PhoneNumberUtil.getInstance().isValidNumber(pn) == false) {
                return location;
            }
            location = PhoneNumberOfflineGeocoder.getInstance().getDescriptionForNumber(pn, Locale.CHINESE);
        } catch (Exception e) {
            log.error("PhoneNumberGeoHandle getChina() error", e);
        }
        return location;
    }

}