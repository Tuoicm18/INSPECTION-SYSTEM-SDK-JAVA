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
public class DataGroup {

    private byte[] dg1;
    private byte[] dg2;
    private byte[] dg3;
    private byte[] dg13;
}
