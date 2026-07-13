package com.travelmemory.dto;
import jakarta.validation.constraints.NotBlank;
public class PasswordChangeRequest { @NotBlank private String currentPassword; @NotBlank private String newPassword;
 public String getCurrentPassword(){return currentPassword;} public void setCurrentPassword(String value){currentPassword=value;}
 public String getNewPassword(){return newPassword;} public void setNewPassword(String value){newPassword=value;} }
