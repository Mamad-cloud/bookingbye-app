# Adds a localized resource (Localizable.strings, InfoPlist.strings) kept in
# ios/App/App/<lang>.lproj/ to the Xcode project as a variant group, and its
# languages to knownRegions. Safe to run again with more languages.
#   python scripts/xcode-add-localized.py ios/App/App.xcodeproj/project.pbxproj <filename> <lang> [<lang> ...]
import hashlib, re, sys
pbx, name, langs = sys.argv[1], sys.argv[2], sys.argv[3:]
s = open(pbx, encoding='utf-8').read()
def hid(key): return hashlib.md5(key.encode()).hexdigest().upper()[:24]
group, build = hid('group:' + name), hid('build:' + name)
ftype = 'text.plist.strings'
if f'{group} /* {name} */ = {{' not in s:
    s = s.replace('/* End PBXBuildFile section */',
      f'\t\t{build} /* {name} in Resources */ = {{isa = PBXBuildFile; fileRef = {group} /* {name} */; }};\n/* End PBXBuildFile section */', 1)
    s = s.replace('/* Begin PBXVariantGroup section */',
      f'/* Begin PBXVariantGroup section */\n\t\t{group} /* {name} */ = {{\n\t\t\tisa = PBXVariantGroup;\n\t\t\tchildren = (\n\t\t\t);\n\t\t\tname = {name};\n\t\t\tsourceTree = "<group>";\n\t\t}};', 1)
    s, n = re.subn(r'(/\* App \*/ = \{\n\t\t\tisa = PBXGroup;\n\t\t\tchildren = \(\n)', lambda m: m.group(1) + f'\t\t\t\t{group} /* {name} */,\n', s, count=1)
    assert n == 1
    s, n = re.subn(r'(/\* Resources \*/ = \{\n\t\t\tisa = PBXResourcesBuildPhase;\n\t\t\tbuildActionMask = \d+;\n\t\t\tfiles = \(\n)', lambda m: m.group(1) + f'\t\t\t\t{build} /* {name} in Resources */,\n', s, count=1)
    assert n == 1
for lang in langs:
    ref = hid(f'{lang}:{name}')
    if f'{ref} /* {lang} */' in s: continue
    s = s.replace('/* End PBXFileReference section */',
      f'\t\t{ref} /* {lang} */ = {{isa = PBXFileReference; lastKnownFileType = {ftype}; name = {lang}; path = {lang}.lproj/{name}; sourceTree = "<group>"; }};\n/* End PBXFileReference section */', 1)
    s, n = re.subn(r'(\t\t' + group + r' /\* ' + re.escape(name) + r' \*/ = \{\n\t\t\tisa = PBXVariantGroup;\n\t\t\tchildren = \(\n)', lambda m: m.group(1) + f'\t\t\t\t{ref} /* {lang} */,\n', s, count=1)
    assert n == 1
    m = re.search(r'knownRegions = \(\n(.*?)\t\t\t\);', s, re.S)
    if not re.search(r'\b' + re.escape(lang) + r',', m.group(1)):
        s = s[:m.end(1)] + f'\t\t\t\t{lang},\n' + s[m.end(1):]
open(pbx, 'w', encoding='utf-8', newline='\n').write(s)
print('ok', name, langs)
