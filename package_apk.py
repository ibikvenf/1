#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys
import zipfile
import hashlib
import base64
import datetime
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs7

# Constants
TEMPLATE_APK_PATH = "/tmp/rebuilt.apk"
OUTPUT_APK_PATH = "/home/user/1/gobang.apk"
GAME_ASSETS_DIR = "/home/user/1/app/src/main/assets/www"

def generate_key_and_cert():
    print("[+] 正在生成自签名证书和 RSA 私钥...")
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    
    subject = issuer = x509.Name([
        x509.NameAttribute(x509.NameOID.COMMON_NAME, u"Gomoku AI"),
        x509.NameAttribute(x509.NameOID.ORGANIZATION_NAME, u"Arena"),
        x509.NameAttribute(x509.NameOID.COUNTRY_NAME, u"US"),
    ])
    
    cert = x509.CertificateBuilder().subject_name(
        subject
    ).issuer_name(
        issuer
    ).public_key(
        private_key.public_key()
    ).serial_number(
        x509.random_serial_number()
    ).not_valid_before(
        datetime.datetime.utcnow() - datetime.timedelta(days=1)
    ).not_valid_after(
        datetime.datetime.utcnow() + datetime.timedelta(days=3650) # 10 years validity
    ).sign(private_key, hashes.SHA256())
    
    return private_key, cert

def format_manifest_entry(name, digest):
    # Formats manifest entry according to JAR spec
    entry = f"Name: {name}\r\nSHA-256-Digest: {digest}\r\n\r\n"
    return entry.encode('utf-8')

def build_manifest_and_sf(zip_items, cert_sf_digest_manifest):
    manifest_data = b"Manifest-Version: 1.0\r\nCreated-By: 1.0 (Android SignApk)\r\n\r\n"
    sf_data = b"Signature-Version: 1.0\r\nCreated-By: 1.0 (Android SignApk)\r\n"
    sf_data += f"SHA-256-Digest-Manifest: {cert_sf_digest_manifest}\r\n\r\n".encode('utf-8')
    
    for name, item_info in zip_items.items():
        # Skip META-INF files
        if name.startswith("META-INF/"):
            continue
            
        data, _ = item_info
        # 1. Add file to MANIFEST.MF
        file_hash = hashlib.sha256(data).digest()
        file_digest = base64.b64encode(file_hash).decode('utf-8')
        manifest_entry = format_manifest_entry(name, file_digest)
        manifest_data += manifest_entry
        
        # 2. Add entry to CERT.SF (hash of the manifest entry)
        entry_hash = hashlib.sha256(manifest_entry).digest()
        entry_digest = base64.b64encode(entry_hash).decode('utf-8')
        sf_entry = format_manifest_entry(name, entry_digest)
        sf_data += sf_entry
        
    return manifest_data, sf_data

def write_aligned_entry(zout, filename, data, compress_type):
    # Construct standard ZipInfo
    zinfo = zipfile.ZipInfo(filename)
    zinfo.compress_type = compress_type
    
    # Enable datetime for compatibility
    zinfo.date_time = (2026, 7, 24, 8, 16, 0)
    
    # 4-byte Alignment constraint for uncompressed files (ZIP_STORED)
    # This matches the 'zipalign' optimization requirement on Android 11+ (API 30+)
    if compress_type == zipfile.ZIP_STORED:
        current_offset = zout.fp.tell()
        # Local file header size is 30 bytes + length of filename
        lfh_size = 30 + len(filename.encode('utf-8'))
        payload_offset = current_offset + lfh_size
        
        padding = (4 - (payload_offset % 4)) % 4
        if padding > 0:
            # Pad the local header's extra field with null bytes to align the data payload to 4 bytes boundary
            zinfo.extra = b"\x00" * padding
            
    zout.writestr(zinfo, data)

def package_and_sign():
    if not os.path.exists(TEMPLATE_APK_PATH):
        print(f"[-] 错误：找不到基础 APK 模板 {TEMPLATE_APK_PATH}！", file=sys.stderr)
        return False
        
    print("[+] 正在读取基础 APK 模板...")
    zip_items = {} # filename -> (data, compress_type)
    with zipfile.ZipFile(TEMPLATE_APK_PATH, 'r') as zin:
        for info in zin.infolist():
            zip_items[info.filename] = (zin.read(info.filename), info.compress_type)
            
    # Inject our Gobang game assets
    print("[+] 正在将智能五子棋游戏资源注入到 APK 中...")
    game_files = {
        "index.html": "index.html",
        "style.css": "style.css",
        "game.js": "game.js",
        "nn_model.js": "nn_model.js",
        "audio.js": "audio.js"
    }
    
    for game_file, apk_target in game_files.items():
        local_path = os.path.join(GAME_ASSETS_DIR, game_file)
        if os.path.exists(local_path):
            with open(local_path, 'rb') as f:
                content = f.read()
                # Overwrite assets/web/, assets/local/, and assets/demo/ to be absolutely robust
                # Since HTML/JS/CSS files should be compressed, we write them with ZIP_DEFLATED
                zip_items[f"assets/web/{apk_target}"] = (content, zipfile.ZIP_DEFLATED)
                print(f"  -> 已注入: assets/web/{apk_target}")
                if game_file == "index.html":
                    zip_items["assets/local/index.html"] = (content, zipfile.ZIP_DEFLATED)
                    zip_items["assets/demo/index.html"] = (content, zipfile.ZIP_DEFLATED)
        else:
            print(f"[-] 警告：找不到游戏资源文件 {local_path}！", file=sys.stderr)
            
    # Clean up old META-INF signatures
    old_meta_files = [f for f in zip_items.keys() if f.startswith("META-INF/")]
    for f in old_meta_files:
        del zip_items[f]
        
    # Generate signature keys
    private_key, cert = generate_key_and_cert()
    
    # Calculate manifest and signature blocks
    print("[+] 正在计算签名校验和哈希链...")
    # First, we need a dummy manifest to compute digest for CERT.SF header
    dummy_manifest = b"Manifest-Version: 1.0\r\nCreated-By: 1.0 (Android SignApk)\r\n\r\n"
    for name, item_info in zip_items.items():
        if name.startswith("META-INF/"): continue
        data, _ = item_info
        file_hash = hashlib.sha256(data).digest()
        file_digest = base64.b64encode(file_hash).decode('utf-8')
        dummy_manifest += format_manifest_entry(name, file_digest)
        
    manifest_digest = base64.b64encode(hashlib.sha256(dummy_manifest).digest()).decode('utf-8')
    
    # Generate true Manifest and CERT.SF
    manifest_data, sf_data = build_manifest_and_sf(zip_items, manifest_digest)
    
    # Create PKCS7 signature of CERT.SF
    print("[+] 正在通过 Cryptography 模块进行 PKCS7 (v1 JAR) 证书签名...")
    signature_builder = pkcs7.PKCS7SignatureBuilder()
    signature_builder = signature_builder.set_data(sf_data)
    signature_builder = signature_builder.add_signer(cert, private_key, hashes.SHA256())
    cert_rsa_data = signature_builder.sign(serialization.Encoding.DER, [
        pkcs7.PKCS7Options.DetachedSignature,
        pkcs7.PKCS7Options.NoCapabilities
    ])
    
    # Add files back to zip_items with ZIP_DEFLATED (or ZIP_STORED, but metadata manifest can be compressed)
    zip_items["META-INF/MANIFEST.MF"] = (manifest_data, zipfile.ZIP_DEFLATED)
    zip_items["META-INF/CERT.SF"] = (sf_data, zipfile.ZIP_DEFLATED)
    zip_items["META-INF/CERT.RSA"] = (cert_rsa_data, zipfile.ZIP_DEFLATED)
    
    # Write everything back to a new APK file with alignment optimization
    print(f"[+] 正在写入最终打包并签名的 APK 文件: {OUTPUT_APK_PATH}...")
    with zipfile.ZipFile(OUTPUT_APK_PATH, 'w') as zout:
        # Write META-INF files first (recommended for compatibility)
        meta_keys = [k for k in zip_items.keys() if k.startswith("META-INF/")]
        for k in meta_keys:
            data, compress_type = zip_items[k]
            write_aligned_entry(zout, k, data, compress_type)
            
        # Write all other assets
        for k, item_info in zip_items.items():
            if not k.startswith("META-INF/"):
                data, compress_type = item_info
                write_aligned_entry(zout, k, data, compress_type)
                
    print("[+] APK 打包、4字节对齐及证书签名成功！")
    return True

if __name__ == "__main__":
    success = package_and_sign()
    sys.exit(0 if success else 1)
