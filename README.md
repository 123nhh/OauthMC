This plugin allows you to link up Oauth to your minecraft server to force any player who joins to register through Oauth, which may be helpful for limiting access to a server to only people in a specific organization (such as a school, or a company).

I will not provide help or support with registering Oauth applications. You may follow these two guides, but additionally please read the quote blocks below corresponding to microsoft/google for additional information. The two guides: [Microsoft Guide](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app?tabs=certificate) and JUST THE PREREQUISITES SECTION of the [Google Guide](https://developers.google.com/identity/protocols/oauth2/limited-input-device#prerequisites). 

If at any point while using a company account you find a step which seems to be impossible, your organization may have blocked that feature for you. In that case, either ask that they allow for you to use that feature or create the registration on a personal account.

<details>
<summary>Microsoft Additional Info:</summary>
In the "Register an Application" section, if you are creating this on a personal account, click "Accounts in any organizational directory and personal Microsoft accounts." If you are creating it on an account which is linked to the organization which you want to verify users are coming from, first verify that you are registering the app within the organization by clicking the settings button in the top right and checking that the organization you want to register under is marked as "Current" with a green check mark. Then, creating the application as per the guide, select "Accounts in this organizational directory only." Remember what you selected for later! You do not need to set a "redirect URI" and please skip that section. In the "Configure platform settings" section, select "Mobile and desktop applications" and click the https://login.live.com/oauth20_desktop.srf checkbox. Then, down below, set "Allow public client flows" to Yes and click save. After that, go back to the application overview (just below the smaller search box). Copy the "Application (client) ID" into the microsoft client ID in the config under "microsoft-settings". If you were creating this on a personal account earlier, in the config section for the plugin set the "tenant" to "common". Otherwise, copy the "Directory (tenant) ID" on the same page and set "tenant" to that value.
</details>

<details>
<summary>Google Additional Info:</summary>
> If it asks you to create a project and gives you the option of creating it within a certain organization, select the organization you wish to allow to log in to the Minecraft server. When going through the "OAuth consent screen" configuration, if you were able to register your project within the organization, you should select "Internal." If you can't, just select external. On the first page, configure all of the mandatory input fields and avoid doing any other ones as some have additional requirements. In scopes, press "Add or remove scopes" and select the ones ending in userinfo.email, userinfo.profile, and openid. In my experience those three should be at the top and be blank in the "API" column. Do not select ANY SCOPES besides those ones. Now scroll down and press "Update". If you did it right, those 3 scopes should show under "Your non-sensitive scopes" and both the other sections should be empty. You can just press "Save and Continue" and "Back to Dashboard" on the next two screens. Now you will be able to create the credentials as it says in the guide. Copy the Client ID and Client secret into the plugins config under "google-settings". Finally, go back to the "OAuth consent screen" menu and if under "Publishing Status" it says "Testing" press the "Publish App" button below it, and then press "Confirm". It should now say the "Publishing Status" is "In production."
</details>


Features:
- Supports SQLite and MySQL for storage of data.
- Oauth through either Google or Microsoft
- Customizable kick message
- Set a specific email suffix to block random logins
- Whitelist specific users in the config to be able to bypass the Oauth requirement
