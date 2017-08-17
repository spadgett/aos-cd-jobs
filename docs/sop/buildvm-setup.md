- Enable RPM repos:
  - Most packages will need this: https://gitlab.cee.redhat.com/platform-eng-core-services/internal-repos/raw/master/rhel/rhel-7.repo
  - For puddle, rhpkg, rhtools, rh-signing-tools: http://download.devel.redhat.com/rel-eng/RCMTOOLS/rcm-tools-rhel-7-server.repo
  - For tito and npm, install EPEL: 
    - wget http://dl.fedoraproject.org/pub/epel/7/x86_64/e/epel-release-7-10.noarch.rpm
    - rpm -ivh epel-release-7-10.noarch.rpm
- yum install
  - go
  - docker
  - git
  - puddle
  - rhpkg
  - brew (yum install koji)
  - tito
  - rhtools  (required for sign_unsigned.py)
  - rh-signing-tools  (required for sign_unsigned.py)
  - npm (needed for origin-web-console asset compilation)
  - pip (`yum install python-pip`)
  - virtualenv (`pip install virtualenv`)
- Install oc client compatible with Ops registry (https://console.reg-aws.openshift.com/console/)
  - wget https://mirror.openshift.com/pub/openshift-v3/clients/3.6.170/linux/oc.tar.gz
  - extract 'oc' binary in /usr/bin
- Mounts in fstab
  - ntap-bos-c01-eng01-nfs01a.storage.bos.redhat.com:/devops_engarchive2_nfs /mnt/engarchive2 nfs tcp,ro,nfsvers=3 0 0
  - ntap-bos-c01-eng01-nfs01b.storage.bos.redhat.com:/devops_engineering_nfs/devarchive/redhat /mnt/redhat nfs tcp,ro,nfsvers=3 0 0
- Enabling RPM signing
  - RPM signing is limited by hostname. Presently, only openshift-build-1.lab.eng.rdu.redhat.com as ocp-build kerberos id has this authority. The hostname is tied to the MAC of the server.
  - The system must be plugged into a lab Ethernet port. Port 16W306A was joined to the engineering network for this purpose.
- Setup "jenkins" user
  - Create user
    - If user home must be in anywhere non-default (such as on an NFS share) advanced steps will be required:
      - \# Assuming a mount at /mnt/nfs
      - mkdir /mnt/nfs/home
      - semanage fcontext -a -e /home /mnt/nfs/home/jenkins # Tell SELinux this path is allowed
      - restorecon /mnt/nfs/home
      - useradd -m -d /mnt/nfs/home/jenkins jenkins
  - Ensure user has at least 100GB in home directory (Jenkins server will run as jenkins user and workspace will reside here).
  - Add `jenkins    ALL=(ALL)    NOPASSWD: ALL` to the bottom of /etc/sudoers (https://serverfault.com/questions/160581/how-to-setup-passwordless-sudo-on-linux)
  - Create "docker" group and add "jenkins" user to enable docker daemon operations without sudo.
- Configure git
  - `git config --global user.name "Jenkins CD Merge Bot"`
  - `git config --global user.email smunilla@redhat.com`  (or current build point-of-contact)
  - `git config --global push.default simple`
- Configure docker 
  - You should use a production configuration of devicemapper/thinpool for docker with at least 150GB of storage in the VG
  - Edit /etc/sysconfig/docker and set the following: `INSECURE_REGISTRY='--insecure-registry brew-pulp-docker01.web.prod.ext.phx2.redhat.com:8888 --insecure-registry rcm-img-docker01.build.eng.bos.redhat.com:5001 --insecure-registry registry.access.stage.redhat.com'`
- Configure tito
  - Populate ~/.titorc with `RHPKG_USER=ocp-build`
- In a temporary directory
  - git clone https://github.com/openshift/origin-web-console.git
  - cd origin-web-console
  - ./hack/install-deps.sh  (necessary for pre-processing origin-web-console files)
  - sudo npm install -g grunt-cli bower
- Establish known hosts (and accept fingerprints):
  - ssh to github.com
  - ssh to rcm-guest.app.eng.bos.redhat.com
  - ssh to pkgs.devel.redhat.com
- Credentials
  - Copy /home/jenkins/.ssh/id_rsa from existing buildvm into place on new buildvm. This is necessary to ssh as ocp-build to rcm-guest. Ideally, this credential will be pulled into Jenkins credential store soon.

- Setup host as a Jenkins agent 
  - Copy /home/jenkins/swarm-client-2.0-jar-with-dependencies.jar off old buildvm and into place on new buildvm.
  - Populate /etc/systemd/system/swarm.service (ensure that -name parameter is unique and -labels are the desired ones):

```
[Unit]
After=network-online.target
Wants=network-online.target

[Service]
ExecStart=/usr/bin/nohup /usr/bin/java -Xmx2048m -jar /home/jenkins/swarm-client-2.0-jar-with-dependencies.jar -master https://atomic-e2e-jenkins.rhev-ci-vms.eng.rdu2.redhat.com/ -name buildvm-devops.usersys.redhat.com -executors 10 -labels "openshift-build openshift-build-1" -fsroot /home/jenkins -mode exclusive -disableSslVerification -disableClientsUniqueId
Restart=on-failure
User=jenkins
Group=jenkins

[Install]
WantedBy=multi-user.target
```

- Reload systemctl daemon (`sudo systemctl daemon-reload`)
  - Set swarm to autostart (`sudo systemctl enable swarm`)
- Create the following repos on buildvm

```
# /etc/yum.repos.d/dockertested.repo 
[dockertested]
name=Latest tested version of Docker
baseurl=https://mirror.openshift.com/enterprise/rhel/dockertested/x86_64/os/
failovermethod=priority
enabled=0
priority=40
gpgcheck=0
sslverify=0
sslclientcert=/var/lib/yum/client-cert.pem
sslclientkey=/var/lib/yum/client-key.pem



# /etc/yum.repos.d/rhel7next.repo 
[rhel7next]
name=Prerelease version of Enterprise Linux 7.x
baseurl=https://mirror.openshift.com/enterprise/rhel/rhel7next/os/
failovermethod=priority
enabled=0
priority=40
gpgcheck=0
sslverify=0
sslclientcert=/var/lib/yum/client-cert.pem
sslclientkey=/var/lib/yum/client-key.pem

[rhel7next-optional]
name=Prerelease version of Enterprise Linux 7.x
baseurl=https://mirror.openshift.com/enterprise/rhel/rhel7next/optional/
failovermethod=priority
enabled=0
priority=40
gpgcheck=0
sslverify=0
sslclientcert=/var/lib/yum/client-cert.pem
sslclientkey=/var/lib/yum/client-key.pem

[rhel7next-extras]
name=Prerelease version of Enterprise Linux 7.x
baseurl=https://mirror.openshift.com/enterprise/rhel/rhel7next/extras/
failovermethod=priority
enabled=0
priority=40
gpgcheck=0
sslverify=0
sslclientcert=/var/lib/yum/client-cert.pem
sslclientkey=/var/lib/yum/client-key.pem
```
