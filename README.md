# qnpdf
A PDF tool [Scala script](https://ammonite.io/#ScalaScripts)  

Usage:
1. Install amm first 

  ```
 sudo sh -c '(echo "#!/usr/bin/env sh" && curl -L https://github.com/com-lihaoyi/Ammonite/releases/download/2.3.8/2.13-2.3.8) > /usr/local/bin/amm && chmod +x /usr/local/bin/amm' && amm
 ```

2. To generate outline to some big scanned pdf book 

```
amm qnpdf.sc outline -f pathToSomeScannedPdfWithoutOutline.pdf -n 20
```

