Note: Must be built with Java 8.

# IBM Continuous Release

---

With this Jenkins plugin, you can run Jenkins jobs as a part of a deployment plan in IBM Continuous Release (CR) and Composite Pipeline.  You can run jobs that will trigger the creation of a version in the Composite Pipeline as well as persist properties on that version that can be used as input properties in other Jenkins builds.  This plugin will pass along important data from Git to the Composite Pipeline.

# Detailed Functionality

* **Posting job metadata to your Continuous Release instance -** As you create and edit jobs, the metadata for the jobs will uploaded including the names of the jobs as well as the names of parameters.  This is done so that the jobs can be invoked from the Continuous Release service within the CR security model.

* **Invoke jobs from Continuous Release -** An authenticated, encrypted persistent connection is established with the CR service so that you can trigger off jobs and pipelines with no special firewall configuration.

* **Updates status of running jobs -** You will receive instant feedback in the CR service with links to the execution.

* **Job Executions can create versions in Composite Pipeline -** You will have the option to select Jenkins jobs as "input jobs" which will create a version with special properties that you can specify.

* **Detects quality data provided by IBM Deployment Risk Analytics -** If you use the capabilities found in the IBM Cloud DevOps plugin to provide data to IBM DRA, then this plugin will forward that data to your composite pipeline to visualize quality data across your whole suite of applications.

# Installation

## 1. Generate a Sync Id and Sync Token

Navigate to the [Getting Started page](https://console.bluemix.net/devops/continuous-release/getting-started) of the Continuous Release service and at the bottom of the page, click 'Setup' under the Jenkins section to open the "Setup Jenkins Integration" dialog box.

![Getting Started Page](screenshots/getting-started-page.png)

In the dialog box provide a logical name that represents the Jenkins instance that you are attempting to connect.  This will generate a Sync Id and Sync Token pair.  When the time comes this dialog provides "Copy to Clipboard" button on these fields.

![Getting Started Dialog](screenshots/getting-started-dialog.png)

## 2. Install this plugin on your Jenkins instance

Navigate to the plugins page on your Jenkins instance by clicking `Manage Jenkins > Manage Plugins > Available (tab)` and search for `IBM Continuous Release`.  When located install the plugin and restart your instance when possible.

If the plugin is not available in the Jenkins publically hosted plugins, please download [ibm-continuous-release.hpi](http://public.dhe.ibm.com/software/products/UrbanCode/plugins/) and upload it to your Jenkins instance by navigating to the Advanced tab on the plugins page.

## 3. Populate Jenkins Configuration with Sync Id, Sync Token, and Jenkins Credentials

Navigate to the Jenkins configuration page `Manage Jenkins > Configure System > IBM Continuous Release (section)`.  Under the IBM Continuous Release section paste the Sync ID and Sync Token values from Step 1 above.  Please add a credentials entry for a Jenkins user on whose behalf this plugin may access your Jenkins items.  Please `Apply` or save the values before clicking the `Test Connection` button to confirm your connection to the CR serivce.  Upon successful connection, your data will be posted to your IBM Bluemix Organization.

![Global Config Page](screenshots/global-config-page.png)

## License

Copyright&copy; 2016, 2017 IBM Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

