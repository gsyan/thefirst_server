import re
import os
import subprocess
import platform

def open_file_location(file_path):
    """생성된 파일이 있는 폴더를 운영체제의 기본 파일 탐색기로 열기"""
    try:
        abs_path = os.path.abspath(file_path)
        folder_path = os.path.dirname(abs_path)

        system = platform.system()
        if system == "Windows":
            subprocess.run(f'explorer /select,"{abs_path}"', shell=True)
        elif system == "Darwin":  # macOS
            subprocess.run(['open', '-R', abs_path], check=True)
        elif system == "Linux":
            subprocess.run(['xdg-open', folder_path], check=True)
        else:
            print(f"Unsupported operating system: {system}")
            return

        print(f"Opened file location: {folder_path}")
    except Exception as e:
        print(f"Failed to open file location: {e}")
        print(f"File location: {os.path.abspath(file_path)}")

def map_csharp_type_to_java(csharp_type):
    """C# 타입을 Java 타입으로 매핑"""
    type_mapping = {
        'int': 'Integer',
        'long': 'Long',
        'float': 'Float',
        'double': 'Double',
        'bool': 'Boolean',
        'string': 'String',
        'short': 'Short',
        'byte': 'Byte',
    }

    # nullable 타입 처리 (long? -> Long) - ? 제거
    if csharp_type.endswith('?'):
        csharp_type = csharp_type[:-1]

    # 배열 타입 처리 (ShipInfo[] -> List<ShipInfo>)
    if csharp_type.endswith('[]'):
        element_type = csharp_type[:-2]
        java_element_type = map_csharp_type_to_java(element_type)
        return f'List<{java_element_type}>'

    # enum 타입 (E로 시작)은 그대로 유지
    if csharp_type.startswith('E'):
        return csharp_type

    # 기본 타입 매핑
    mapped_type = type_mapping.get(csharp_type, csharp_type)

    # 커스텀 타입 (DTO 클래스)에는 Dto 접미사 추가
    # 기본 타입(Integer, String 등)이 아닌 경우
    if mapped_type == csharp_type and csharp_type not in type_mapping:
        # Request/Response는 Dto 불필요
        if csharp_type.endswith('Request') or csharp_type.endswith('Response'):
            return csharp_type
        return f"{csharp_type}Dto"

    return mapped_type

def extract_all_classes(csharp_content):
    """C# 파일에서 모든 클래스 추출"""
    # [System.Serializable] public class ClassName { ... } 패턴 찾기
    class_pattern = r'\[System\.Serializable\]\s*\n\s*public\s+class\s+(\w+)(?:<(\w+)>)?\s*\{([^}]*(?:\{[^}]*\}[^}]*)*)\}'

    classes = []
    for match in re.finditer(class_pattern, csharp_content, re.DOTALL | re.MULTILINE):
        class_name = match.group(1)
        generic_type = match.group(2)  # ApiResponse<T> 같은 제네릭 타입
        class_body = match.group(3)

        # 주석 처리된 클래스는 건너뛰기
        # [System.Serializable] 바로 앞 줄만 확인 (다른 주석과 혼동 방지)
        start_pos = match.start()
        before_lines = csharp_content[:start_pos].split('\n')

        # [System.Serializable] 어트리뷰트 라인 찾기
        attribute_line_idx = -1
        for i in range(len(before_lines) - 1, max(len(before_lines) - 10, -1), -1):
            if '[System.Serializable]' in before_lines[i]:
                attribute_line_idx = i
                break

        # 어트리뷰트 바로 앞 줄이 주석인지 확인
        is_commented = False
        if attribute_line_idx > 0:
            prev_line = before_lines[attribute_line_idx - 1].strip()
            is_commented = prev_line.startswith('//')

        if is_commented:
            continue

        classes.append({
            'name': class_name,
            'generic_type': generic_type,
            'body': class_body
        })

    return classes

def extract_fields(class_body):
    """클래스 본문에서 public 필드 추출"""
    fields = []
    lines = class_body.split('\n')

    for line in lines:
        stripped = line.strip()

        # 주석이나 빈 줄 건너뛰기
        if not stripped or stripped.startswith('//'):
            continue

        # 메서드나 프로퍼티 건너뛰기 (=> 포함)
        if '=>' in stripped or '{' in stripped or '}' in stripped:
            continue

        # public 필드 추출 (타입 필드명;) - nullable 타입(?) 지원, @params 같은 특수 케이스 지원
        field_pattern = r'public\s+(\w+(?:\[\])?\??)\s+(@?\w+);'
        field_match = re.match(field_pattern, stripped)

        if field_match:
            field_type = field_match.group(1)
            field_name = field_match.group(2)

            # @params 같은 특수 케이스 처리
            if field_name.startswith('@'):
                field_name = field_name[1:]  # @ 제거

            # Java 타입으로 매핑
            java_type = map_csharp_type_to_java(field_type)

            fields.append({
                'csharp_name': field_name,
                'java_name': field_name,  # 동일한 이름 사용
                'java_type': java_type
            })

    return fields

def generate_java_dto(class_info, package_name):
    """단일 DTO 클래스의 Java 코드 생성"""
    original_class_name = class_info['name']
    generic_type = class_info['generic_type']
    fields = extract_fields(class_info['body'])

    if not fields and not generic_type:
        # 필드가 없고 제네릭도 아닌 경우
        return None

    # Java 클래스명에 Dto 접미사 추가 (Request/Response 제외)
    if original_class_name.endswith('Request') or original_class_name.endswith('Response'):
        java_class_name = original_class_name  # Request/Response는 Dto 불필요
    else:
        java_class_name = f"{original_class_name}Dto"

    # Java 코드 생성
    java_code = f"package {package_name};\n\n"

    # import 문 수집
    imports = set()
    needs_list = False

    for field in fields:
        # List 타입이 필요한지 확인
        if field['java_type'].startswith('List<'):
            needs_list = True

        # enum 타입 import
        if field['java_type'].startswith('E'):
            imports.add(f"import com.bk.sbs.enums.{field['java_type']};")

        # 제네릭 안의 타입도 확인
        if '<' in field['java_type']:
            inner_type = re.search(r'<(\w+)>', field['java_type']).group(1)
            if inner_type.startswith('E'):
                imports.add(f"import com.bk.sbs.enums.{inner_type};")

    # import 문 추가
    if needs_list:
        imports.add("import java.util.List;")

    imports.add("import lombok.Data;")
    imports.add("import lombok.NoArgsConstructor;")  # 모든 DTO에 필요 (Jackson 역직렬화용)

    # Request 클래스 판별 (원본 클래스명 기준)
    is_request = original_class_name.endswith('Request')

    if not is_request:
        # Response/Info: Builder + AllArgsConstructor도 추가
        imports.add("import lombok.Builder;")
        imports.add("import lombok.AllArgsConstructor;")

    for imp in sorted(imports):
        java_code += f"{imp}\n"

    java_code += "\n"
    java_code += "/**\n"
    java_code += f" * {java_class_name}\n"
    java_code += f" * Auto-generated from Unity C# {original_class_name} class\n"
    java_code += " */\n"
    java_code += "@Data\n"
    java_code += "@NoArgsConstructor\n"  # 모든 DTO에 추가 (Jackson 역직렬화용)
    if not is_request:
        java_code += "@Builder\n"
        java_code += "@AllArgsConstructor\n"

    # 클래스 선언
    if generic_type:
        java_code += f"public class {java_class_name}<{generic_type}> {{\n"
    else:
        java_code += f"public class {java_class_name} {{\n"

    # 필드 정의
    for field in fields:
        java_code += f"    private {field['java_type']} {field['java_name']};\n"

    java_code += "}\n"

    return java_code

def generate_all_dtos(csharp_file_path, output_dir, package_name):
    """모든 DTO 생성"""
    # 제외할 클래스 목록 (클라이언트 전용)
    EXCLUDED_CLASSES = {'ApiResponse'}

    # C# 파일 읽기
    with open(csharp_file_path, 'r', encoding='utf-8') as file:
        content = file.read()

    # 모든 클래스 추출
    classes = extract_all_classes(content)

    print(f"Found {len(classes)} classes in {csharp_file_path}")

    generated_files = []

    # 각 클래스별로 Java 파일 생성
    for class_info in classes:
        class_name = class_info['name']

        # 제외 목록 확인
        if class_name in EXCLUDED_CLASSES:
            print(f"Skipped {class_name} (excluded - client only)")
            continue

        # Java 코드 생성
        java_code = generate_java_dto(class_info, package_name)

        if not java_code:
            print(f"Skipped {class_name} (no fields or generation failed)")
            continue

        # 출력 파일 경로 (Request/Response 제외하고 Dto 접미사 추가)
        if class_name.endswith('Request') or class_name.endswith('Response'):
            java_class_name = class_name
        else:
            java_class_name = f"{class_name}Dto"
        output_file_path = os.path.join(output_dir, f"{java_class_name}.java")

        # 출력 디렉토리 생성
        os.makedirs(output_dir, exist_ok=True)

        # Java 파일 쓰기
        with open(output_file_path, 'w', encoding='utf-8') as file:
            file.write(java_code)

        generated_files.append(output_file_path)
        print(f"Generated: {java_class_name}.java")

    print(f"\nTotal generated: {len(generated_files)} files")

    if generated_files:
        # 첫 번째 생성된 파일 위치 열기
        open_file_location(generated_files[0])

    return generated_files

if __name__ == "__main__":
    csharp_file_path = r"../../../thefirst_client_unity/Assets/Scripts/System/Network/NetworkDTOs.cs"
    output_dir = r"../../src/main/java/com/bk/sbs/dto"
    package_name = "com.bk.sbs.dto"

    generate_all_dtos(csharp_file_path, output_dir, package_name)
