import re
import json

with open(r'C:\Users\giang\.gemini\antigravity-ide\brain\8ef65e0e-8a51-4c92-984f-d7f03c10b4ae\scratch\aula_pdp.html', 'r', encoding='utf-8') as f:
    html = f.read()

# Search for __moduleData__
idx = html.find('var __moduleData__ =')
if idx != -1:
    end = html.find('</script>', idx)
    content = html[idx + len('var __moduleData__ ='):end].strip()
    if content.endswith(';'):
        content = content[:-1].strip()
    data = json.loads(content)
    root_fields = data.get('data', {}).get('root', {}).get('fields', {})
    print('root_fields keys:', list(root_fields.keys()))
    sku_infos = root_fields.get('skuInfos', {})
    print('Number of skuInfos:', len(sku_infos))
    # print sample sku
    for k, v in list(sku_infos.items())[:2]:
        print('SKU Key:', k)
        print('  price:', v.get('price'))
        print('  stock:', v.get('stock'))
        print('  saleProp:', v.get('saleProp'))
        print('  skuId:', v.get('skuId'))
    product = root_fields.get('product', {})
    print('Product title:', product.get('title'))
    print('Product itemId:', product.get('itemId'))
