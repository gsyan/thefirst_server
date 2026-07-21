import re
import os
import subprocess
import platform

def open_file_location(file_path):
    try:
        abs_path = os.path.abspath(file_path)
        system = platform.system()
        if system == "Windows":
            subprocess.run(f'explorer /select,"{abs_path}"', shell=True)
        elif system == "Darwin":
            subprocess.run(['open', '-R', abs_path], check=True)
        elif system == "Linux":
            subprocess.run(['xdg-open', os.path.dirname(abs_path)], check=True)
        print(f"Opened: {abs_path}")
    except Exception as e:
        print(f"Failed to open file location: {e}")

def map_csharp_type_to_java(csharp_type):
    """C# 타입 → Java 타입 변환"""
    type_mapping = {
        'int': 'Integer', 'long': 'Long', 'float': 'Float',
        'double': 'Double', 'bool': 'Boolean', 'string': 'String',
        'short': 'Short', 'byte': 'Byte',
    }

    # nullable (long? → Long)
    if csharp_type.endswith('?'):
        csharp_type = csharp_type[:-1]

    # List<T>
    list_match = re.match(r'List<(\w+)>', csharp_type)
    if list_match:
        return f'List<{map_csharp_type_to_java(list_match.group(1))}>'

    # int[][] → List<List<Integer>>
    if csharp_type.endswith('[][]'):
        return f'List<List<{map_csharp_type_to_java(csharp_type[:-4])}>>'

    # T[] → List<T>
    if csharp_type.endswith('[]'):
        return f'List<{map_csharp_type_to_java(csharp_type[:-2])}>'

    # enum (E + 대문자, 예: EUnitState) 그대로 유지 — E로 시작하는 일반 클래스(ExplorationFleetInfo 등)와 구분
    if re.match(r'^E[A-Z]', csharp_type):
        return csharp_type

    mapped = type_mapping.get(csharp_type, csharp_type)
    if mapped == csharp_type and csharp_type not in type_mapping:
        if csharp_type.endswith('Request') or csharp_type.endswith('Response'):
            return csharp_type
        return f"{csharp_type}Dto"

    return mapped

def extract_all_classes(csharp_content):
    """[System.Serializable] 클래스 추출. 바로 앞 줄이 // 주석이면 건너뜀."""
    # 클래스 body: 생성자 등 1단계 중첩 {} 지원
    class_pattern = r'\[System\.Serializable\]\s*\n\s*public\s+class\s+(\w+)(?:<(\w+)>)?\s*\{([^}]*(?:\{[^}]*\}[^}]*)*)\}'

    classes = []
    for match in re.finditer(class_pattern, csharp_content, re.DOTALL | re.MULTILINE):
        # [System.Serializable] 바로 앞 줄이 주석이면 건너뜀 (의도적 제외 마킹용)
        before_text = csharp_content[:match.start()]
        prev_line = before_text.rstrip('\n').rsplit('\n', 1)[-1].strip()
        if prev_line.startswith('//'):
            continue

        classes.append({
            'name': match.group(1),
            'generic_type': match.group(2),
            'body': match.group(3),
        })

    return classes

def extract_fields(class_body):
    """클래스 body에서 public 필드 추출"""
    fields = []
    field_pattern = r'public\s+((?:\w+(?:<\w+>)?(?:\[\]){0,2}\??))\s+(@?\w+)(?:\s*=\s*[^;]+)?;'

    for line in class_body.split('\n'):
        stripped = line.strip()
        if not stripped or stripped.startswith('//'):
            continue
        if '=>' in stripped or '{' in stripped or '}' in stripped:
            continue

        m = re.match(field_pattern, stripped)
        if m:
            field_name = m.group(2).lstrip('@')
            fields.append({
                'java_name': field_name,
                'java_type': map_csharp_type_to_java(m.group(1)),
            })

    return fields

def generate_java_dto(class_info, package_name):
    """단일 DTO Java 코드 생성"""
    original_name = class_info['name']
    generic_type = class_info['generic_type']
    fields = extract_fields(class_info['body'])

    is_request = original_name.endswith('Request')
    java_class_name = original_name if (original_name.endswith('Request') or original_name.endswith('Response')) else f"{original_name}Dto"

    imports = {'import lombok.Data;', 'import lombok.NoArgsConstructor;'}
    if not is_request:
        imports.add('import lombok.Builder;')
        if fields:
            imports.add('import lombok.AllArgsConstructor;')

    for f in fields:
        jt = f['java_type']
        if jt.startswith('List<'):
            imports.add('import java.util.List;')
        # enum import (E + 대문자로 시작하는 타입, 예: EUnitState)
        inner = re.search(r'(\bE[A-Z]\w*)', jt)
        if inner:
            imports.add(f"import com.bk.sbs.enums.{inner.group(1)};")

    code = f"package {package_name};\n\n"
    for imp in sorted(imports):
        code += f"{imp}\n"
    code += "\n"
    code += f"/**\n * {java_class_name}\n * Auto-generated from Unity C# {original_name} class\n */\n"
    code += "@Data\n@NoArgsConstructor\n"
    if not is_request:
        code += "@Builder\n"
        if fields:
            code += "@AllArgsConstructor\n"

    code += f"public class {java_class_name}"
    if generic_type:
        code += f"<{generic_type}>"
    code += " {\n"
    for f in fields:
        code += f"    private {f['java_type']} {f['java_name']};\n"
    code += "}\n"

    return code

def generate_all_dtos(csharp_file_path, output_dir, package_name):
    EXCLUDED_CLASSES = {'ApiResponse'}

    with open(csharp_file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    classes = extract_all_classes(content)
    print(f"Found {len(classes)} classes in {csharp_file_path}")

    generated_files = []
    for class_info in classes:
        name = class_info['name']
        if name in EXCLUDED_CLASSES:
            print(f"Skipped {name} (excluded)")
            continue

        java_code = generate_java_dto(class_info, package_name)
        java_class_name = name if (name.endswith('Request') or name.endswith('Response')) else f"{name}Dto"
        output_path = os.path.join(output_dir, f"{java_class_name}.java")

        os.makedirs(output_dir, exist_ok=True)
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(java_code)

        generated_files.append(output_path)
        print(f"Generated: {java_class_name}.java")

    print(f"\nTotal generated: {len(generated_files)} files")
    if generated_files:
        open_file_location(generated_files[0])

    return generated_files

if __name__ == "__main__":
    output_dir = r"../../src/main/java/com/bk/sbs/dto"
    package_name = "com.bk.sbs.dto"

    generate_all_dtos(
        r"../../../thefirst_client_unity/Assets/Scripts/System/Network/NetworkDTOs.cs",
        output_dir, package_name
    )
