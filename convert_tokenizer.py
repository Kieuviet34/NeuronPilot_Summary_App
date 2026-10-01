import json
import struct

def convert_tokenizer(json_path, bin_path):
    print(f"Loading {json_path}...")
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    vocab = data['model']['vocab']
    
    print(f"Writing {bin_path}...")
    with open(bin_path, 'wb') as f:
        for text, id in vocab.items():
            # Format: [int ID][int len][string text]
            f.write(struct.pack('i', id))
            encoded_text = text.encode('utf-8')
            f.write(struct.pack('i', len(encoded_text)))
            f.write(encoded_text)
            
    print("Done! Hãy copy file 'tokenizer.model' vào thư mục 'assets' của app.")

if __name__ == "__main__":
    # Script này sẽ tạo ra file 'tokenizer.model' đã được tối ưu hóa cho App
    convert_tokenizer('tokenizer.json', 'tokenizer.model')
