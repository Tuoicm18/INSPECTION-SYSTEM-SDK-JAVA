/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.mobileid.icao.sdk.message.resp;

import lombok.Builder;
import lombok.Getter;

/**
 *
 * @author TRUONGNNT
 */
@Getter
@Builder
public class OptionalDetails {

    private String personalNumber;
    private String fullName;
    private String birthDate;
    private String gender;
    private String nationality;
    private String ethnic;

    private String religion;
    private String placeOfOrigin;
    private String placeOfResidence;
    private String personalIdentification;
    private String issuanceDate;
    private String expiryDate;

    private String idDocument;
    private String fullNameOfFather;
    private String fullNameOfMother;
    private String fullNameOfSpouse;
}
