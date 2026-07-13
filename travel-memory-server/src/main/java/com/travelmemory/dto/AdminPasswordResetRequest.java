package com.travelmemory.dto;
import jakarta.validation.constraints.NotBlank;
public class AdminPasswordResetRequest { @NotBlank private String temporaryPassword; public String getTemporaryPassword(){return temporaryPassword;} public void setTemporaryPassword(String value){temporaryPassword=value;} }
